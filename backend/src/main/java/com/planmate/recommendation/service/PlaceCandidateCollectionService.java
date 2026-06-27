package com.planmate.recommendation.service;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.PlaceSearchCandidate;
import com.planmate.place.dto.PlaceTextSearchRequest;
import com.planmate.place.dto.PlaceTextSearchResponse;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.place.service.GooglePlacesService;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CandidateSearchAnchor;
import com.planmate.recommendation.domain.CandidateSearchQuery;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.recommendation.service.PlaceCandidateAccumulator.CategorizedPlaceSearchCandidate;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.entity.TripPlanningProfileEntity;
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
public class PlaceCandidateCollectionService {

    public static final int DEFAULT_TARGET_CANDIDATE_COUNT = 120;
    public static final int DEFAULT_MAX_RAW_CANDIDATE_COUNT = 180;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private static final Set<String> BLOCKED_TYPES = Set.of(
            "locality",
            "political",
            "country",
            "administrative_area_level_1",
            "administrative_area_level_2",
            "administrative_area_level_3",
            "postal_code",
            "route",
            "street_address",
            "lodging",
            "parking",
            "atm",
            "bank",
            "gas_station",
            "real_estate_agency"
    );

    private final GooglePlacesService googlePlacesService;
    private final CandidateCategoryWeightCalculator weightCalculator;
    private final CandidateSearchQueryFactory queryFactory;
    private final HaversineDistanceCalculator distanceCalculator;
    private final PlaceCandidateAccumulator accumulator;
    private final CandidateScorer scorer;
    private final CandidateSelector selector;
    private final CandidateSearchAnchorResolver searchAnchorResolver;
    private final int targetCandidateCount;
    private final int maxRawCandidateCount;
    private final int pageSize;
    private final double maxDistanceMeters;

    public PlaceCandidateCollectionService(
            GooglePlacesService googlePlacesService,
            CandidateCategoryWeightCalculator weightCalculator,
            CandidateSearchQueryFactory queryFactory,
            HaversineDistanceCalculator distanceCalculator,
            PlaceCandidateAccumulator accumulator,
            CandidateScorer scorer,
            CandidateSelector selector,
            CandidateSearchAnchorResolver searchAnchorResolver,
            @Value("${app.itinerary.candidates.target-count:120}") int targetCandidateCount,
            @Value("${app.itinerary.candidates.max-raw-count:180}") int maxRawCandidateCount,
            @Value("${app.itinerary.candidates.page-size:20}") int pageSize,
            @Value("${app.itinerary.candidates.max-distance-meters:50000}") double maxDistanceMeters
    ) {
        this.googlePlacesService = googlePlacesService;
        this.weightCalculator = weightCalculator;
        this.queryFactory = queryFactory;
        this.distanceCalculator = distanceCalculator;
        this.accumulator = accumulator;
        this.scorer = scorer;
        this.selector = selector;
        this.searchAnchorResolver = searchAnchorResolver;
        this.targetCandidateCount = targetCandidateCount;
        this.maxRawCandidateCount = maxRawCandidateCount;
        this.pageSize = pageSize;
        this.maxDistanceMeters = maxDistanceMeters;
    }

    public List<CollectedPlaceCandidate> collect(ResolvedDestination destination, TripPlanningProfileEntity profile) {
        Map<CandidateSearchCategory, Integer> weights = weightCalculator.calculate(profile.getInterests());
        List<CandidateSearchQuery> queries = queryFactory.create(destination.displayName(), weights, profile.getInterests());
        CandidateSearchAnchor searchAnchor = searchAnchorResolver.resolve(destination, profile);
        List<CategorizedPlaceSearchCandidate> collected = new ArrayList<>();
        Set<String> uniquePlaceIds = new HashSet<>();
        int rawCount = 0;

        for (CandidateSearchQuery query : queries) {
            String pageToken = null;
            int pagesForQuery = 0;
            do {
                PlaceTextSearchResponse response = googlePlacesService.searchText(new PlaceTextSearchRequest(
                        query.textQuery(),
                        "ko",
                        pageSize,
                        searchAnchor.searchArea(),
                        pageToken
                ));
                rawCount += response.places().size();

                for (PlaceSearchCandidate candidate : response.places()) {
                    if (!isUsableCandidate(candidate, searchAnchor)) {
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
        return mergeForcedCandidates(forcedCandidates(profile, searchAnchor), selectedCandidates);
    }

    private List<CollectedPlaceCandidate> forcedCandidates(
            TripPlanningProfileEntity profile,
            CandidateSearchAnchor searchAnchor
    ) {
        return profile.getMustVisitPlaces()
                .stream()
                .filter(MustVisitPlaceSnapshot::isResolved)
                .map(place -> forcedCandidate(place, searchAnchor))
                .toList();
    }

    private CollectedPlaceCandidate forcedCandidate(
            MustVisitPlaceSnapshot place,
            CandidateSearchAnchor searchAnchor
    ) {
        GeoPoint location = new GeoPoint(place.latitude(), place.longitude());
        double distanceMeters = searchAnchor.location() == null
                ? Double.MAX_VALUE
                : distanceCalculator.distanceMeters(searchAnchor.location(), location);
        return new CollectedPlaceCandidate(
                place.placeId(),
                place.name(),
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

    private boolean isUsableCandidate(PlaceSearchCandidate candidate, CandidateSearchAnchor searchAnchor) {
        if (!StringUtils.hasText(candidate.displayName()) || !StringUtils.hasText(candidate.placeId())) {
            return false;
        }
        if (candidate.location() == null) {
            return false;
        }
        if (StringUtils.hasText(candidate.businessStatus()) && !"OPERATIONAL".equals(candidate.businessStatus())) {
            return false;
        }
        if (hasBlockedType(candidate)) {
            return false;
        }
        return isWithinSearchRange(candidate.location(), searchAnchor);
    }

    private boolean hasBlockedType(PlaceSearchCandidate candidate) {
        return candidate.types().stream().anyMatch(BLOCKED_TYPES::contains)
                || (candidate.primaryType() != null && BLOCKED_TYPES.contains(candidate.primaryType()));
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
}
