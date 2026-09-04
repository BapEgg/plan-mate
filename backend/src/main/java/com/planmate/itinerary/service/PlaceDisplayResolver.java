package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.itinerary.repository.GenerationCandidateSnapshotRepository;
import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.PlaceDisplay;
import com.planmate.place.api.PlaceDisplayReader;
import com.planmate.place.api.exception.InvalidPlaceIdException;
import com.planmate.place.api.exception.PlaceProviderConfigurationException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlaceDisplayResolver {

    private final PlaceDisplayReader placeDisplayReader;
    private final GenerationCandidateSnapshotRepository candidateSnapshotRepository;

    public PlaceDisplayResolver(
            PlaceDisplayReader placeDisplayReader,
            GenerationCandidateSnapshotRepository candidateSnapshotRepository
    ) {
        this.placeDisplayReader = placeDisplayReader;
        this.candidateSnapshotRepository = candidateSnapshotRepository;
    }

    public Map<String, ItineraryPlaceDisplayView> resolveListViews(List<String> placeIds) {
        return resolveListViews(null, placeIds);
    }

    public Map<String, ItineraryPlaceDisplayView> resolveListViews(Long tripId, List<String> placeIds) {
        Map<String, ItineraryPlaceDisplayView> result = new LinkedHashMap<>();
        Map<String, ItineraryPlaceDisplayView> savedSnapshots = savedSnapshots(tripId, placeIds);
        boolean providerUnavailable = false;
        for (String placeId : placeIds) {
            if (!StringUtils.hasText(placeId) || result.containsKey(placeId)) {
                continue;
            }
            if (providerUnavailable) {
                result.put(placeId, savedSnapshots.getOrDefault(placeId, ItineraryPlaceDisplayView.unresolved()));
                continue;
            }
            try {
                result.put(placeId, resolveProviderView(placeId));
            } catch (InvalidPlaceIdException exception) {
                result.put(placeId, savedSnapshots.getOrDefault(placeId, ItineraryPlaceDisplayView.unresolved()));
            } catch (PlaceProviderUnavailableException | PlaceProviderConfigurationException exception) {
                providerUnavailable = true;
                result.put(placeId, savedSnapshots.getOrDefault(placeId, ItineraryPlaceDisplayView.unresolved()));
            }
        }
        return result;
    }

    private ItineraryPlaceDisplayView resolveProviderView(String placeId) {
        PlaceDisplay display = placeDisplayReader.readDisplay(placeId, "ko");
        return ItineraryPlaceDisplayView.resolved(
                display.displayName(),
                display.location(),
                display.googleMapsUri()
        );
    }

    private Map<String, ItineraryPlaceDisplayView> savedSnapshots(Long tripId, List<String> placeIds) {
        if (tripId == null || placeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ItineraryPlaceDisplayView> snapshots = new LinkedHashMap<>();
        candidateSnapshotRepository.findLatestTripSnapshots(tripId, placeIds).forEach(candidate ->
                snapshots.putIfAbsent(candidate.getPlaceId(), toSavedView(candidate))
        );
        return snapshots;
    }

    private ItineraryPlaceDisplayView toSavedView(GenerationCandidateSnapshotEntity candidate) {
        return ItineraryPlaceDisplayView.saved(
                candidate.getName(),
                new GeoPoint(candidate.getLatitude(), candidate.getLongitude())
        );
    }
}
