package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.exception.TripNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ItineraryPlaceViewServiceTest {

    private final TripAccessChecker tripAccessChecker = Mockito.mock(TripAccessChecker.class);
    private final ItineraryRepository itineraryRepository = Mockito.mock(ItineraryRepository.class);
    private final PlaceDisplayResolver placeDisplayResolver = Mockito.mock(PlaceDisplayResolver.class);
    private final ItineraryPlaceViewService service = new ItineraryPlaceViewService(
            tripAccessChecker,
            itineraryRepository,
            placeDisplayResolver
    );

    @Test
    void checksTripAccessBeforeReadingCurrentItineraryPlaces() {
        given(itineraryRepository.findCurrentByTripId(45L)).willReturn(Optional.empty());

        assertThat(service.listLatestItineraryPlaceViews(7L, 45L, null)).isEmpty();

        verify(tripAccessChecker).checkAccessible(7L, 45L);
        verify(itineraryRepository).findCurrentByTripId(45L);
        verifyNoInteractions(placeDisplayResolver);
    }

    @Test
    void doesNotReadItineraryWhenTripIsNotAccessible() {
        Mockito.doThrow(new TripNotFoundException())
                .when(tripAccessChecker)
                .checkAccessible(7L, 45L);

        assertThatThrownBy(() -> service.listLatestItineraryPlaceViews(7L, 45L, null))
                .isInstanceOf(TripNotFoundException.class);

        verifyNoInteractions(itineraryRepository, placeDisplayResolver);
    }
}
