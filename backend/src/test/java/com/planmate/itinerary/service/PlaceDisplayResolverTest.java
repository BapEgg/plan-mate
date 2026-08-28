package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
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
    private final PlaceDisplayResolver resolver = new PlaceDisplayResolver(placeDisplayReader);

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
}
