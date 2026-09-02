package com.planmate.regeneration.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.service.GenerationCandidateSnapshotStore;
import com.planmate.itinerary.service.PlaceDisplayResolver;
import com.planmate.itinerary.repository.ItineraryRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RegenerationValidationCandidateResolver {

    private final ItineraryRepository itineraryRepository;
    private final GenerationCandidateSnapshotStore candidateSnapshotStore;
    private final PlaceDisplayResolver placeDisplayResolver;

    public RegenerationValidationCandidateResolver(
            ItineraryRepository itineraryRepository,
            GenerationCandidateSnapshotStore candidateSnapshotStore,
            PlaceDisplayResolver placeDisplayResolver
    ) {
        this.itineraryRepository = itineraryRepository;
        this.candidateSnapshotStore = candidateSnapshotStore;
        this.placeDisplayResolver = placeDisplayResolver;
    }

    public List<GenerationCandidateSnapshot> resolve(
            ItineraryEntity base,
            List<GenerationCandidateSnapshot> collectedCandidates
    ) {
        List<GenerationCandidateSnapshot> safeCollected = collectedCandidates == null
                ? List.of()
                : List.copyOf(collectedCandidates);
        Map<String, GenerationCandidateSnapshot> resolved = new LinkedHashMap<>();
        safeCollected.forEach(candidate -> resolved.putIfAbsent(candidate.placeId(), candidate));

        Set<String> currentPlaceIds = currentPlaceIds(base);
        Map<String, GenerationCandidateSnapshot> historical = historicalCandidates(base);
        List<String> unresolved = new ArrayList<>();
        int nextRank = resolved.size() + 1;
        for (String placeId : currentPlaceIds) {
            if (resolved.containsKey(placeId)) {
                continue;
            }
            GenerationCandidateSnapshot candidate = historical.get(placeId);
            if (candidate == null) {
                unresolved.add(placeId);
                continue;
            }
            resolved.put(placeId, copyForValidation(nextRank++, candidate));
        }

        if (!unresolved.isEmpty()) {
            Map<String, ItineraryPlaceDisplayView> displays = placeDisplayResolver.resolveListViews(unresolved);
            for (String placeId : unresolved) {
                ItineraryPlaceDisplayView display = displays.get(placeId);
                if (display == null || !display.resolved() || display.location() == null) {
                    continue;
                }
                resolved.put(placeId, fromDisplay(nextRank++, placeId, display));
            }
        }
        return List.copyOf(resolved.values());
    }

    private Set<String> currentPlaceIds(ItineraryEntity base) {
        Set<String> result = new LinkedHashSet<>();
        base.getDays().stream()
                .sorted(java.util.Comparator.comparingInt(day -> day.getDay()))
                .forEach(day -> day.getItems().stream()
                        .sorted(java.util.Comparator.comparingInt(item -> item.getSequence()))
                        .forEach(item -> result.add(item.getPlaceId())));
        return result;
    }

    private Map<String, GenerationCandidateSnapshot> historicalCandidates(ItineraryEntity base) {
        Map<String, GenerationCandidateSnapshot> result = new LinkedHashMap<>();
        Set<Long> visited = new HashSet<>();
        ItineraryEntity cursor = base;
        while (cursor != null && cursor.getId() != null && visited.add(cursor.getId())) {
            if (cursor.getGeneration() != null) {
                candidateSnapshotStore.findAllByGenerationId(cursor.getGeneration().getId())
                        .forEach(candidate -> result.putIfAbsent(candidate.placeId(), candidate));
            }
            Long parentId = cursor.getBaseItineraryId();
            cursor = parentId == null ? null : itineraryRepository.findById(parentId).orElse(null);
        }
        return result;
    }

    private GenerationCandidateSnapshot copyForValidation(int rank, GenerationCandidateSnapshot source) {
        return new GenerationCandidateSnapshot(
                rank,
                source.placeId(),
                source.displayName(),
                source.formattedAddress(),
                source.location(),
                source.primaryType(),
                source.types(),
                source.businessStatus(),
                source.rating(),
                source.userRatingCount(),
                source.openingPeriods(),
                source.sourceCategories(),
                false,
                source.distanceMeters(),
                source.score()
        );
    }

    private GenerationCandidateSnapshot fromDisplay(
            int rank,
            String placeId,
            ItineraryPlaceDisplayView display
    ) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                display.displayName(),
                null,
                new GenerationCandidateSnapshot.Location(
                        display.location().latitude(),
                        display.location().longitude()
                ),
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of("CURRENT_ITINERARY"),
                false,
                null,
                0
        );
    }
}
