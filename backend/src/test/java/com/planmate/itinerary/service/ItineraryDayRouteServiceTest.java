package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.route.RouteCoordinate;
import com.planmate.itinerary.route.RoutePath;
import com.planmate.itinerary.route.kakao.KakaoDrivingRouteProvider;
import com.planmate.itinerary.service.ItineraryDayRoutePlanReader.DayRouteItem;
import com.planmate.itinerary.service.ItineraryDayRoutePlanReader.DayRoutePlan;
import com.planmate.place.api.GeoPoint;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItineraryDayRouteServiceTest {

    @Test
    void returnsVerifiedKakaoLegsForAdjacentItemsOnly() {
        ItineraryDayRoutePlanReader planReader = mock(ItineraryDayRoutePlanReader.class);
        PlaceDisplayResolver displayResolver = mock(PlaceDisplayResolver.class);
        KakaoDrivingRouteProvider provider = mock(KakaoDrivingRouteProvider.class);
        ItineraryDayRouteService service = new ItineraryDayRouteService(planReader, displayResolver, provider);
        DayRoutePlan plan = new DayRoutePlan(50L, 3, 2, List.of(
                new DayRouteItem(101L, 1, "a"),
                new DayRouteItem(102L, 2, "b")
        ));
        when(planReader.read(7L, 9L, 2)).thenReturn(plan);
        when(displayResolver.resolveListViews(9L, List.of("a", "b"))).thenReturn(Map.of(
                "a", ItineraryPlaceDisplayView.resolved("A", new GeoPoint(34.8, 128.6), null),
                "b", ItineraryPlaceDisplayView.resolved("B", new GeoPoint(34.9, 128.7), null)
        ));
        when(provider.findDetailedRoute(any(), any())).thenReturn(Optional.of(new RoutePath(
                "KAKAO", 12500, 1500,
                List.of(new RouteCoordinate(34.8, 128.6), new RouteCoordinate(34.9, 128.7)),
                Instant.parse("2026-09-01T00:00:00Z")
        )));

        var response = service.getDayRoute(7L, 9L, 2);

        assertThat(response.itineraryId()).isEqualTo(50L);
        assertThat(response.itineraryVersion()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.legs()).singleElement().satisfies(leg -> {
            assertThat(leg.fromItemId()).isEqualTo(101L);
            assertThat(leg.toItemId()).isEqualTo(102L);
            assertThat(leg.durationSeconds()).isEqualTo(1500);
            assertThat(leg.geometry()).hasSize(2);
        });
    }

    @Test
    void unresolvedPlaceReturnsPartialWithoutCallingRouteProvider() {
        ItineraryDayRoutePlanReader planReader = mock(ItineraryDayRoutePlanReader.class);
        PlaceDisplayResolver displayResolver = mock(PlaceDisplayResolver.class);
        KakaoDrivingRouteProvider provider = mock(KakaoDrivingRouteProvider.class);
        ItineraryDayRouteService service = new ItineraryDayRouteService(planReader, displayResolver, provider);
        when(planReader.read(7L, 9L, 1)).thenReturn(new DayRoutePlan(50L, 3, 1, List.of(
                new DayRouteItem(101L, 1, "a"), new DayRouteItem(102L, 2, "b")
        )));
        when(displayResolver.resolveListViews(9L, List.of("a", "b"))).thenReturn(Map.of(
                "a", ItineraryPlaceDisplayView.resolved("A", new GeoPoint(34.8, 128.6), null),
                "b", ItineraryPlaceDisplayView.unresolved()
        ));

        var response = service.getDayRoute(7L, 9L, 1);

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.legs()).singleElement()
                .extracting(leg -> leg.status())
                .isEqualTo("LOCATION_UNRESOLVED");
        verify(provider, never()).findDetailedRoute(any(), any());
    }
}
