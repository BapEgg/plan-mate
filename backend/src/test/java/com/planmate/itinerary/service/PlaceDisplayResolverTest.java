package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.itinerary.repository.GenerationCandidateSnapshotRepository;
import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.PlaceDisplay;
import com.planmate.place.api.PlaceDisplayReader;
import com.planmate.place.api.exception.InvalidPlaceIdException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaceDisplayResolverTest {

    private final PlaceDisplayReader placeDisplayReader = Mockito.mock(PlaceDisplayReader.class);
    private final GenerationCandidateSnapshotRepository candidateSnapshotRepository =
            Mockito.mock(GenerationCandidateSnapshotRepository.class);
    private final PlaceDisplayResolver resolver = new PlaceDisplayResolver(
            placeDisplayReader,
            candidateSnapshotRepository
    );

    @Test
    void resolvesDisplayAndSkipsDuplicatePlaceIds() {
        given(placeDisplayReader.readDisplay("place-1", "ko"))
                .willReturn(new PlaceDisplay(
                        "place-1",
                        "Museum",
                        new GeoPoint(35.0, 135.0),
                        "https://maps.example/place-1"
                ));

        Map<String, ItineraryPlaceDisplayView> result = resolver.resolveListViews(List.of("place-1", "place-1"));

        assertThat(result).containsOnlyKeys("place-1");
        assertThat(result.get("place-1")).satisfies(display -> {
            assertThat(display.resolved()).isTrue();
            assertThat(display.displayName()).isEqualTo("Museum");
            assertThat(display.location()).isEqualTo(new GeoPoint(35.0, 135.0));
            assertThat(display.googleMapsUri()).isEqualTo("https://maps.example/place-1");
            assertThat(display.source()).isEqualTo("PROVIDER");
        });
        verify(placeDisplayReader, times(1)).readDisplay("place-1", "ko");
    }

    @Test
    void invalidPlaceIdFallsBackToUnresolvedDisplay() {
        given(placeDisplayReader.readDisplay("bad-place", "ko"))
                .willThrow(new InvalidPlaceIdException());

        Map<String, ItineraryPlaceDisplayView> result = resolver.resolveListViews(List.of("bad-place"));

        assertThat(result.get("bad-place").resolved()).isFalse();
    }

    @Test
    void providerUnavailableFallsBackToUnresolvedDisplay() {
        given(placeDisplayReader.readDisplay("place-1", "ko"))
                .willThrow(new PlaceProviderUnavailableException());

        Map<String, ItineraryPlaceDisplayView> result = resolver.resolveListViews(List.of("place-1"));

        assertThat(result.get("place-1").resolved()).isFalse();
    }

    @Test
    void providerOutageUsesLatestSavedTripSnapshotsAndStopsRepeatedProviderCalls() {
        GenerationCandidateSnapshotEntity first = savedCandidate("place-1", "매미성", 34.88, 128.62);
        GenerationCandidateSnapshotEntity second = savedCandidate("place-2", "바람의 언덕", 34.74, 128.67);
        given(candidateSnapshotRepository.findLatestTripSnapshots(
                1530L,
                List.of("place-1", "place-2")
        )).willReturn(List.of(first, second));
        given(placeDisplayReader.readDisplay("place-1", "ko"))
                .willThrow(new PlaceProviderUnavailableException());

        Map<String, ItineraryPlaceDisplayView> result = resolver.resolveListViews(
                1530L,
                List.of("place-1", "place-2")
        );

        assertThat(result.get("place-1")).satisfies(display -> {
            assertThat(display.resolved()).isTrue();
            assertThat(display.displayName()).isEqualTo("매미성");
            assertThat(display.location()).isEqualTo(new GeoPoint(34.88, 128.62));
            assertThat(display.source()).isEqualTo("SAVED_SNAPSHOT");
        });
        assertThat(result.get("place-2")).satisfies(display -> {
            assertThat(display.resolved()).isTrue();
            assertThat(display.displayName()).isEqualTo("바람의 언덕");
            assertThat(display.source()).isEqualTo("SAVED_SNAPSHOT");
        });
        verify(placeDisplayReader, times(1)).readDisplay("place-1", "ko");
        verify(placeDisplayReader, never()).readDisplay("place-2", "ko");
    }

    private GenerationCandidateSnapshotEntity savedCandidate(
            String placeId,
            String name,
            double latitude,
            double longitude
    ) {
        GenerationCandidateSnapshotEntity candidate = Mockito.mock(GenerationCandidateSnapshotEntity.class);
        given(candidate.getPlaceId()).willReturn(placeId);
        given(candidate.getName()).willReturn(name);
        given(candidate.getLatitude()).willReturn(latitude);
        given(candidate.getLongitude()).willReturn(longitude);
        return candidate;
    }
}
