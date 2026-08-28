package com.planmate.recommendation.service;

import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.PlaceSearchCandidate;
import com.planmate.place.api.PlaceTextSearchQuery;
import com.planmate.place.api.PlaceTextSearchResult;
import com.planmate.place.api.PlaceTextSearcher;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.CandidateRecommender;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CandidateSearchAnchor;
import com.planmate.recommendation.domain.CandidateSearchQuery;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.recommendation.domain.PlaceTypePolicyRule;
import com.planmate.recommendation.service.PlaceCandidateAccumulator.CategorizedPlaceSearchCandidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlaceCandidateCollectionService implements CandidateRecommender {

    public static final int DEFAULT_TARGET_CANDIDATE_COUNT = 120;
    public static final int DEFAULT_MAX_RAW_CANDIDATE_COUNT = 180;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final PlaceTextSearcher placeTextSearcher;
    private final CandidateCategoryWeightCalculator weightCalculator;
    private final CandidateSearchQueryFactory queryFactory;
    private final HaversineDistanceCalculator distanceCalculator;
    private final PlaceCandidateAccumulator accumulator;
    private final CandidateScorer scorer;
    private final CandidateSelector selector;
    private final CandidateSearchAnchorResolver searchAnchorResolver;
    private final PlaceTypePolicyService placeTypePolicyService;
    private final int targetCandidateCount;
    private final int maxRawCandidateCount;
    private final int pageSize;
    private final double maxDistanceMeters;

    public PlaceCandidateCollectionService(
            PlaceTextSearcher placeTextSearcher,
            CandidateCategoryWeightCalculator weightCalculator,
            CandidateSearchQueryFactory queryFactory,
            HaversineDistanceCalculator distanceCalculator,
            PlaceCandidateAccumulator accumulator,
            CandidateScorer scorer,
            CandidateSelector selector,
            CandidateSearchAnchorResolver searchAnchorResolver,
            PlaceTypePolicyService placeTypePolicyService,
            @Value("${app.itinerary.candidates.target-count:120}") int targetCandidateCount,
            @Value("${app.itinerary.candidates.max-raw-count:180}") int maxRawCandidateCount,
            @Value("${app.itinerary.candidates.page-size:20}") int pageSize,
            @Value("${app.itinerary.candidates.max-distance-meters:50000}") double maxDistanceMeters
    ) {
        this.placeTextSearcher = placeTextSearcher;
        this.weightCalculator = weightCalculator;
        this.queryFactory = queryFactory;
        this.distanceCalculator = distanceCalculator;
        this.accumulator = accumulator;
        this.scorer = scorer;
        this.selector = selector;
        this.searchAnchorResolver = searchAnchorResolver;
        this.placeTypePolicyService = placeTypePolicyService;
        this.targetCandidateCount = targetCandidateCount;
        this.maxRawCandidateCount = maxRawCandidateCount;
        this.pageSize = pageSize;
        this.maxDistanceMeters = maxDistanceMeters;
    }

    @Override
    public List<RecommendedPlaceCandidate> recommend(CandidateRecommendationRequest request) {
        Map<CandidateSearchCategory, Integer> weights = weightCalculator.calculate(request.interests());
        List<CandidateSearchQuery> queries = queryFactory.create(request.destination().displayName(), weights, request.interests());
        CandidateSearchAnchor searchAnchor = searchAnchorResolver.resolve(request.destination(), request.accommodation());
        Map<String, PlaceTypePolicyRule> typePolicies = placeTypePolicyService.loadEnabledPoliciesByTypeName();
        List<CategorizedPlaceSearchCandidate> collected = new ArrayList<>();
        Set<String> uniquePlaceIds = new HashSet<>();
        int rawCount = 0;

        for (CandidateSearchQuery query : queries) {
            String pageToken = null;
            int pagesForQuery = 0;
            do {
                PlaceTextSearchResult response = placeTextSearcher.searchText(new PlaceTextSearchQuery(
                        query.textQuery(),
                        "ko",
                        pageSize,
                        searchAnchor.searchArea(),
                        pageToken
                ));
                rawCount += response.places().size();

                for (PlaceSearchCandidate candidate : response.places()) {
                    if (!isUsableCandidate(candidate, searchAnchor, typePolicies)) {
                        continue;
                    }
                    double distanceMeters = distanceCalculator.distanceMeters(searchAnchor.location(), candidate.location());
                    collected.add(new CategorizedPlaceSearchCandidate(candidate, query.category(), distanceMeters));
                    uniquePlaceIds.add(candidate.placeId());
                }

                if (uniquePlaceIds.size() >= targetCandidateCount || rawCount >= maxRawCandidateCount) {
                    break;
                }
                pageToken = response.nextPageToken();
                pagesForQuery++;
            } while (StringUtils.hasText(pageToken) && pagesForQuery < 3);

            if (uniquePlaceIds.size() >= targetCandidateCount || rawCount >= maxRawCandidateCount) {
                break;
            }
        }

        List<CollectedPlaceCandidate> scoredCandidates = accumulator.accumulate(collected)
                .stream()
                .map(candidate -> candidate.withScore(scorer.score(candidate, weights)))
                .toList();
        List<CollectedPlaceCandidate> selectedCandidates = selector.select(scoredCandidates, weights, targetCandidateCount);
        List<CollectedPlaceCandidate> finalCandidates = mergeForcedCandidates(
                forcedCandidates(request.mustVisitPlaces(), searchAnchor),
                selectedCandidates
        );
        return toRecommendedCandidates(finalCandidates);
    }

    private List<CollectedPlaceCandidate> forcedCandidates(
            List<CandidateRecommendationRequest.MustVisitPlace> mustVisitPlaces,
            CandidateSearchAnchor searchAnchor
    ) {
        return mustVisitPlaces
                .stream()
                .filter(CandidateRecommendationRequest.MustVisitPlace::isResolved)
                .map(place -> forcedCandidate(place, searchAnchor))
                .toList();
    }

    private CollectedPlaceCandidate forcedCandidate(
            CandidateRecommendationRequest.MustVisitPlace place,
            CandidateSearchAnchor searchAnchor
    ) {
        GeoPoint location = toPoint(place.location());
        double distanceMeters = searchAnchor.location() == null
                ? Double.MAX_VALUE
                : distanceCalculator.distanceMeters(searchAnchor.location(), location);
        return new CollectedPlaceCandidate(
                place.placeId(),
                place.displayName(),
                place.formattedAddress(),
                location,
                place.primaryType(),
                place.types(),
                "OPERATIONAL",
                null,
                null,
                List.of(),
                List.of(CandidateSearchCategory.MUST_VISIT),
                distanceMeters,
                Double.MAX_VALUE
        );
    }

    private List<RecommendedPlaceCandidate> toRecommendedCandidates(List<CollectedPlaceCandidate> candidates) {
        List<RecommendedPlaceCandidate> recommended = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            recommended.add(toRecommendedCandidate(candidates.get(index), index + 1));
        }
        return List.copyOf(recommended);
    }

    private RecommendedPlaceCandidate toRecommendedCandidate(CollectedPlaceCandidate candidate, int rank) {
        return new RecommendedPlaceCandidate(
                rank,
                candidate.placeId(),
                candidate.name(),
                candidate.address(),
                toLocation(candidate.location()),
                candidate.primaryType(),
                candidate.types(),
                candidate.businessStatus(),
                candidate.rating(),
                candidate.userRatingCount(),
                candidate.openingPeriods(),
                sourceCategoryNames(candidate.sourceCategories()),
                candidate.sourceCategories().contains(CandidateSearchCategory.MUST_VISIT),
                candidate.distanceMeters(),
                candidate.score()
        );
    }

    private List<String> sourceCategoryNames(List<CandidateSearchCategory> sourceCategories) {
        return sourceCategories.stream()
                .map(Enum::name)
                .toList();
    }

    private List<CollectedPlaceCandidate> mergeForcedCandidates(
            List<CollectedPlaceCandidate> forcedCandidates,
            List<CollectedPlaceCandidate> selectedCandidates
    ) {
        LinkedHashMap<String, CollectedPlaceCandidate> merged = new LinkedHashMap<>();
        for (CollectedPlaceCandidate candidate : forcedCandidates) {
            merged.put(candidate.placeId(), candidate);
        }
        for (CollectedPlaceCandidate candidate : selectedCandidates) {
            if (merged.size() >= targetCandidateCount) {
                break;
            }
            merged.putIfAbsent(candidate.placeId(), candidate);
        }
        return new ArrayList<>(merged.values());
    }

    private boolean isUsableCandidate(
            PlaceSearchCandidate candidate,
            CandidateSearchAnchor searchAnchor,
            Map<String, PlaceTypePolicyRule> typePolicies
    ) {
        if (!StringUtils.hasText(candidate.displayName()) || !StringUtils.hasText(candidate.placeId())) {
            return false;
        }
        if (candidate.location() == null) {
            return false;
        }
        if (StringUtils.hasText(candidate.businessStatus()) && !"OPERATIONAL".equals(candidate.businessStatus())) {
            return false;
        }
        if (hasBlockedType(candidate, typePolicies)) {
            return false;
        }
        return isWithinSearchRange(candidate.location(), searchAnchor);
    }

    private boolean hasBlockedType(PlaceSearchCandidate candidate, Map<String, PlaceTypePolicyRule> typePolicies) {
        List<String> types = candidate.types() == null ? List.of() : candidate.types();
        return types.stream().anyMatch(type -> isBlockedType(type, typePolicies))
                || isBlockedType(candidate.primaryType(), typePolicies);
    }

    private boolean isBlockedType(String typeName, Map<String, PlaceTypePolicyRule> typePolicies) {
        if (!StringUtils.hasText(typeName)) {
            return false;
        }
        PlaceTypePolicyRule policy = typePolicies.get(typeName);
        return policy != null && policy.isBlock();
    }

    private boolean isWithinSearchRange(GeoPoint point, CandidateSearchAnchor searchAnchor) {
        if (searchAnchor.viewport() != null && searchAnchor.viewport().contains(point)) {
            return true;
        }
        if (searchAnchor.location() == null) {
            return false;
        }
        return distanceCalculator.distanceMeters(searchAnchor.location(), point) <= maxDistanceMeters;
    }

    private GeoPoint toPoint(CandidateRecommendationRequest.Location location) {
        return location == null ? null : new GeoPoint(location.latitude(), location.longitude());
    }

    private CandidateRecommendationRequest.Location toLocation(GeoPoint location) {
        return location == null
                ? null
                : new CandidateRecommendationRequest.Location(location.latitude(), location.longitude());
    }
}
