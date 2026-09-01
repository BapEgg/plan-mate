package com.planmate.itinerary.route.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.RouteTravelTimePort.RouteTravelTime;
import com.planmate.itinerary.route.RouteTravelTimePort.TravelMode;
import com.planmate.itinerary.route.google.GoogleRoutesAdapter;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RouteTravelTimeRouterTest {

    private static final RoutePoint ORIGIN = new RoutePoint(35.0, 129.0);
    private static final RoutePoint DESTINATION = new RoutePoint(35.1, 129.1);

    @Test
    void drivesGoThroughKakao() {
        KakaoDrivingRouteProvider kakao = mock(KakaoDrivingRouteProvider.class);
        GoogleRoutesAdapter google = mock(GoogleRoutesAdapter.class);
        RouteTravelTime expected = new RouteTravelTime(Duration.ofMinutes(10), 5000);
        when(kakao.findRoute(ORIGIN, DESTINATION)).thenReturn(Optional.of(expected));

        RouteTravelTimeRouter router = new RouteTravelTimeRouter(kakao, google);

        assertThat(router.findRoute(ORIGIN, DESTINATION, TravelMode.DRIVE)).contains(expected);
        verify(google, never()).findRoute(any(), any(), any());
    }

    @Test
    void walkAndBicycleStillGoThroughGoogle() {
        KakaoDrivingRouteProvider kakao = mock(KakaoDrivingRouteProvider.class);
        GoogleRoutesAdapter google = mock(GoogleRoutesAdapter.class);
        RouteTravelTime expected = new RouteTravelTime(Duration.ofMinutes(20), 2000);
        when(google.findRoute(ORIGIN, DESTINATION, TravelMode.WALK)).thenReturn(Optional.of(expected));

        RouteTravelTimeRouter router = new RouteTravelTimeRouter(kakao, google);

        assertThat(router.findRoute(ORIGIN, DESTINATION, TravelMode.WALK)).contains(expected);
        verify(kakao, never()).findRoute(any(), any());
    }
}
