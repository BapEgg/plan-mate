package com.planmate.itinerary.route.kakao;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteLegSnapshotEntity;
import com.planmate.itinerary.route.RouteLegSnapshotRepository;
import com.planmate.itinerary.route.RouteProviderDailyUsageRepository;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.RouteTravelTimePort.RouteTravelTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * spec §10.5: 국내 자동차 route provider는 Kakao Mobility 자동차 길찾기
 * {@code GET /v1/directions}로 고정한다. 다중 경유지 길찾기는 범위 밖이다.
 * 좌표쌍 단위 cache와 KST 기준 일 10,000건 hard cap(재시도 포함)을 이 클래스가 직접 책임진다 —
 * {@link RouteTravelTimeRouter}가 이 provider를 {@code DRIVE} 전용으로 호출한다.
 */
@Service
public class KakaoDrivingRouteProvider {

    private static final String PROVIDER = "KAKAO";
    private static final String OPERATION = "DIRECTIONS";
    private static final String TRAVEL_MODE = "DRIVE";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String BASE_URL = "https://apis-navi.kakaomobility.com";
    private static final String SUCCESS_RESULT_CODE = "0";

    private final RestClient restClient;
    private final String apiKey;
    private final int dailyLimit;
    private final RouteLegSnapshotRepository snapshotRepository;
    private final RouteProviderDailyUsageRepository usageRepository;
    private final Clock clock;

    public KakaoDrivingRouteProvider(
            RestClient.Builder restClientBuilder,
            @Value("${app.kakao.directions.api-key:}") String apiKey,
            @Value("${app.kakao.directions.daily-limit:10000}") int dailyLimit,
            RouteLegSnapshotRepository snapshotRepository,
            RouteProviderDailyUsageRepository usageRepository,
            Clock clock
    ) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        this.dailyLimit = dailyLimit;
        this.snapshotRepository = snapshotRepository;
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<RouteTravelTime> findRoute(RoutePoint origin, RoutePoint destination) {
        assertApiKeyConfigured();

        String cacheKey = cacheKey(origin, destination);
        Optional<RouteLegSnapshotEntity> cached = snapshotRepository.findByTravelModeAndCacheKey(TRAVEL_MODE, cacheKey);
        if (cached.isPresent()) {
            RouteLegSnapshotEntity snapshot = cached.get();
            return Optional.of(new RouteTravelTime(
                    Duration.ofSeconds(snapshot.getDurationSeconds()),
                    snapshot.getDistanceMeters()
            ));
        }

        reserveDailyCall();

        try {
            KakaoDirectionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/directions")
                            .queryParam("origin", coordinateParam(origin))
                            .queryParam("destination", coordinateParam(destination))
                            .build())
                    .headers(this::applyKakaoHeaders)
                    .retrieve()
                    .body(KakaoDirectionsResponse.class);

            return extractTravelTime(response)
                    .map(travelTime -> {
                        saveSnapshot(cacheKey, origin, destination, travelTime);
                        return travelTime;
                    });
        } catch (RestClientResponseException exception) {
            if (isProviderUnavailable(exception.getStatusCode())) {
                throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
            }
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_REQUEST_FAILED, exception);
        } catch (RestClientException exception) {
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private void saveSnapshot(String cacheKey, RoutePoint origin, RoutePoint destination, RouteTravelTime travelTime) {
        try {
            snapshotRepository.save(RouteLegSnapshotEntity.create(
                    TRAVEL_MODE,
                    cacheKey,
                    origin.latitude(),
                    origin.longitude(),
                    destination.latitude(),
                    destination.longitude(),
                    (int) travelTime.distanceMeters(),
                    (int) travelTime.duration().getSeconds(),
                    PROVIDER,
                    Instant.now(clock)
            ));
        } catch (org.springframework.dao.DataIntegrityViolationException raceLoser) {
            // 동시에 같은 좌표쌍을 처음 조회한 다른 요청이 먼저 cache를 채웠다 — 이미 유효한
            // 결과를 갖고 있으므로 무시한다.
        }
    }

    private void reserveDailyCall() {
        LocalDate today = Instant.now(clock).atZone(KST).toLocalDate();
        usageRepository.reserveCall(PROVIDER, OPERATION, today, dailyLimit)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE));
    }

    private Optional<RouteTravelTime> extractTravelTime(KakaoDirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }
        KakaoRoute route = response.routes().getFirst();
        if (route.summary() == null || !SUCCESS_RESULT_CODE.equals(String.valueOf(route.resultCode()))) {
            return Optional.empty();
        }
        return Optional.of(new RouteTravelTime(
                Duration.ofSeconds(route.summary().duration()),
                route.summary().distance()
        ));
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
        }
    }

    private void applyKakaoHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey);
    }

    private String coordinateParam(RoutePoint point) {
        // Kakao는 "경도,위도" 순서를 요구한다 — 위도/경도(RoutePoint) 순서와 반대다.
        return String.format(Locale.ROOT, "%.6f,%.6f", point.longitude(), point.latitude());
    }

    private String cacheKey(RoutePoint origin, RoutePoint destination) {
        return String.format(
                Locale.ROOT,
                "%.5f,%.5f|%.5f,%.5f",
                origin.latitude(), origin.longitude(),
                destination.latitude(), destination.longitude()
        );
    }

    private boolean isProviderUnavailable(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return status == 401
                || status == 403
                || status == 408
                || status == 429
                || statusCode.is5xxServerError();
    }

    private record KakaoDirectionsResponse(List<KakaoRoute> routes) {
    }

    private record KakaoRoute(int resultCode, String resultMsg, KakaoSummary summary) {
    }

    private record KakaoSummary(long distance, long duration) {
    }
}
