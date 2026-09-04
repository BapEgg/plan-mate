package com.planmate.itinerary.route.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteLegSnapshotEntity;
import com.planmate.itinerary.route.RouteLegSnapshotRepository;
import com.planmate.itinerary.route.RouteProviderQuotaService;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class KakaoDrivingRouteProviderTest {

    private static final RoutePoint ORIGIN = new RoutePoint(35.0, 129.0);
    private static final RoutePoint DESTINATION = new RoutePoint(35.1, 129.1);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T15:00:00Z"), ZoneId.of("UTC")
    );

    @Test
    void reservesQuotaThenCallsKakaoAndCachesResultOnCacheMiss() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(eq("DRIVE"), any())).thenReturn(Optional.empty());

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/directions")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("origin", "129.000000,35.000000"))
                .andExpect(queryParam("destination", "129.100000,35.100000"))
                .andRespond(withSuccess(
                        "{\"routes\":[{\"result_code\":0,\"result_msg\":\"ok\",\"summary\":{\"distance\":12345,\"duration\":678},"
                                + "\"sections\":[{\"roads\":[{\"vertexes\":[129.0,35.0,129.05,35.05,129.1,35.1]}]}]}]}",
                        MediaType.APPLICATION_JSON
                ));

        var route = provider.findDetailedRoute(ORIGIN, DESTINATION);

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().distanceMeters()).isEqualTo(12345);
        assertThat(route.orElseThrow().durationSeconds()).isEqualTo(678);
        assertThat(route.orElseThrow().geometry()).hasSize(3);
        assertThat(route.orElseThrow().geometry().get(1).latitude()).isEqualTo(35.05);
        server.verify();
        verify(quotaService).reserve("KAKAO", "DIRECTIONS", LocalDate.of(2026, 9, 2), 10000);
        verify(snapshotRepository, times(1)).save(any(RouteLegSnapshotEntity.class));
    }

    @Test
    void cacheHitSkipsQuotaAndKakaoCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(eq("DRIVE"), any())).thenReturn(Optional.of(
                RouteLegSnapshotEntity.create(
                        "DRIVE", "key", 35.0, 129.0, 35.1, 129.1, 999, 111, "KAKAO", Instant.now(), null
                )
        ));

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );

        var route = provider.findRoute(ORIGIN, DESTINATION);

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().distanceMeters()).isEqualTo(999);
        assertThat(route.orElseThrow().duration()).isEqualTo(Duration.ofSeconds(111));
        server.verify();
        verify(quotaService, never()).reserve(any(), any(), any(), anyInt());
    }

    @Test
    void quotaExceededBecomesServiceUnavailableWithoutCallingKakao() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(any(), any())).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new ItineraryException(ItineraryErrorCode.ROUTE_QUOTA_EXCEEDED))
                .when(quotaService).reserve(any(), any(), any(), anyInt());

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );

        assertThatThrownBy(() -> provider.findRoute(ORIGIN, DESTINATION))
                .isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_QUOTA_EXCEEDED.code()));
        server.verify();
    }

    @Test
    void kakaoResultCodeFailureReturnsEmptyWithoutCaching() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(any(), any())).thenReturn(Optional.empty());

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/directions")))
                .andRespond(withSuccess("{\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThat(provider.findRoute(ORIGIN, DESTINATION)).isEmpty();
        server.verify();
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void providerErrorBecomesServiceUnavailableAfterQuotaAlreadyReserved() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(any(), any())).thenReturn(Optional.empty());

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/directions")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.findRoute(ORIGIN, DESTINATION))
                .isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_QUOTA_EXCEEDED.code()));
        // quota는 실패한 시도에도 이미 소모됐다 — 재시도가 무제한으로 한도를 넘기지 않는다는 것을 보인다.
        verify(quotaService, times(1)).reserve(any(), any(), any(), anyInt());
    }

    @Test
    void providerServerErrorBecomesServiceUnavailableWithoutCaching() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(any(), any())).thenReturn(Optional.empty());

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/directions")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> provider.findDetailedRoute(ORIGIN, DESTINATION))
                .isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE.code()));
        verify(snapshotRepository, never()).save(any());
        server.verify();
    }

    @Test
    void providerReadTimeoutUsesDedicatedTimeoutErrorWithoutCaching() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        when(snapshotRepository.findByTravelModeAndCacheKey(any(), any())).thenReturn(Optional.empty());

        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                builder.baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), "test-key", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/directions")))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "Read timed out",
                            new SocketTimeoutException("Read timed out")
                    );
                });

        assertThatThrownBy(() -> provider.findDetailedRoute(ORIGIN, DESTINATION))
                .isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_TIMEOUT.code()));
        verify(snapshotRepository, never()).save(any());
        server.verify();
    }

    @Test
    void missingApiKeyBecomesServiceUnavailableWithoutTouchingCacheOrQuota() {
        RouteLegSnapshotRepository snapshotRepository = mock(RouteLegSnapshotRepository.class);
        RouteProviderQuotaService quotaService = mock(RouteProviderQuotaService.class);
        KakaoDrivingRouteProvider provider = new KakaoDrivingRouteProvider(
                RestClient.builder().baseUrl(KakaoDirectionsClientConfig.BASE_URL).build(), " ", 10000,
                Duration.ofHours(24), snapshotRepository, quotaService, FIXED_CLOCK
        );

        assertThatThrownBy(() -> provider.findRoute(ORIGIN, DESTINATION))
                .isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE.code()));
        verify(snapshotRepository, never()).findByTravelModeAndCacheKey(any(), any());
        verify(quotaService, never()).reserve(any(), any(), any(), anyInt());
    }
}
