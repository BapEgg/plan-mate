package com.planmate.itinerary.route.kakao;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteCoordinate;
import com.planmate.itinerary.route.RouteLegSnapshotEntity;
import com.planmate.itinerary.route.RouteLegSnapshotRepository;
import com.planmate.itinerary.route.RoutePath;
import com.planmate.itinerary.route.RouteProviderQuotaService;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.RouteTravelTimePort.RouteTravelTime;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 국내 자동차 route provider. 표준 Kakao Mobility {@code GET /v1/directions}만
 * 사용하며 인접한 두 장소 단위의 실제 geometry와 이동 시간을 함께 반환한다.
 */
@Service
public class KakaoDrivingRouteProvider {

    private static final String PROVIDER = "KAKAO";
    private static final String OPERATION = "DIRECTIONS";
    private static final String TRAVEL_MODE = "DRIVE";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String SUCCESS_RESULT_CODE = "0";

    private final RestClient restClient;
    private final String apiKey;
    private final int dailyLimit;
    private final Duration cacheTtl;
    private final RouteLegSnapshotRepository snapshotRepository;
    private final RouteProviderQuotaService quotaService;
    private final Clock clock;

    public KakaoDrivingRouteProvider(
            @Qualifier("kakaoDirectionsRestClient") RestClient restClient,
            @Value("${app.kakao.directions.api-key:}") String apiKey,
            @Value("${app.kakao.directions.daily-limit:10000}") int dailyLimit,
            @Value("${app.kakao.directions.cache-ttl:24h}") Duration cacheTtl,
            RouteLegSnapshotRepository snapshotRepository,
            RouteProviderQuotaService quotaService,
            Clock clock
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.dailyLimit = dailyLimit;
        this.cacheTtl = cacheTtl;
        this.snapshotRepository = snapshotRepository;
        this.quotaService = quotaService;
        this.clock = clock;
    }

    public Optional<RouteTravelTime> findRoute(RoutePoint origin, RoutePoint destination) {
        return findRoutePath(origin, destination, false)
                .map(path -> new RouteTravelTime(
                        Duration.ofSeconds(path.durationSeconds()),
                        path.distanceMeters()
                ));
    }

    public Optional<RoutePath> findDetailedRoute(RoutePoint origin, RoutePoint destination) {
        return findRoutePath(origin, destination, true);
    }

    private Optional<RoutePath> findRoutePath(RoutePoint origin, RoutePoint destination, boolean geometryRequired) {
        assertApiKeyConfigured();

        String cacheKey = cacheKey(origin, destination);
        Optional<RouteLegSnapshotEntity> cached = snapshotRepository.findByTravelModeAndCacheKey(TRAVEL_MODE, cacheKey);
        if (cached.isPresent() && isFresh(cached.get())) {
            RoutePath cachedPath = toPath(cached.get());
            if (!geometryRequired || !cachedPath.geometry().isEmpty()) {
                return Optional.of(cachedPath);
            }
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

            return extractPath(response)
                    .map(path -> {
                        saveSnapshot(cached.orElse(null), cacheKey, origin, destination, path);
                        return path;
                    });
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new ItineraryException(ItineraryErrorCode.ROUTE_QUOTA_EXCEEDED, exception);
            }
            if (isProviderUnavailable(exception.getStatusCode())) {
                throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
            }
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_REQUEST_FAILED, exception);
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_TIMEOUT, exception);
            }
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private boolean isFresh(RouteLegSnapshotEntity snapshot) {
        return !snapshot.getVerifiedAt().isBefore(Instant.now(clock).minus(cacheTtl));
    }

    private RoutePath toPath(RouteLegSnapshotEntity snapshot) {
        return new RoutePath(
                snapshot.getProvider(),
                snapshot.getDistanceMeters(),
                snapshot.getDurationSeconds(),
                decodeGeometry(snapshot.getGeometry()),
                snapshot.getVerifiedAt()
        );
    }

    private void saveSnapshot(
            RouteLegSnapshotEntity existing,
            String cacheKey,
            RoutePoint origin,
            RoutePoint destination,
            RoutePath path
    ) {
        try {
            String geometry = encodeGeometry(path.geometry());
            if (existing != null) {
                existing.refresh(path.distanceMeters(), path.durationSeconds(), path.provider(), path.verifiedAt(), geometry);
                snapshotRepository.save(existing);
                return;
            }
            snapshotRepository.save(RouteLegSnapshotEntity.create(
                    TRAVEL_MODE,
                    cacheKey,
                    origin.latitude(),
                    origin.longitude(),
                    destination.latitude(),
                    destination.longitude(),
                    path.distanceMeters(),
                    path.durationSeconds(),
                    path.provider(),
                    path.verifiedAt(),
                    geometry
            ));
        } catch (org.springframework.dao.DataIntegrityViolationException raceLoser) {
            // 동시에 같은 좌표쌍을 먼저 저장한 요청이 있다. 현재 응답은 이미 유효하므로 그대로 사용한다.
        }
    }

    private void reserveDailyCall() {
        LocalDate today = Instant.now(clock).atZone(KST).toLocalDate();
        quotaService.reserve(PROVIDER, OPERATION, today, dailyLimit);
    }

    private Optional<RoutePath> extractPath(KakaoDirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }
        KakaoRoute route = response.routes().getFirst();
        if (route.summary() == null || !SUCCESS_RESULT_CODE.equals(String.valueOf(route.resultCode()))) {
            return Optional.empty();
        }
        return Optional.of(new RoutePath(
                PROVIDER,
                Math.toIntExact(route.summary().distance()),
                Math.toIntExact(route.summary().duration()),
                extractGeometry(route.sections()),
                Instant.now(clock)
        ));
    }

    private List<RouteCoordinate> extractGeometry(List<KakaoSection> sections) {
        if (sections == null) {
            return List.of();
        }
        List<RouteCoordinate> geometry = new ArrayList<>();
        for (KakaoSection section : sections) {
            if (section == null || section.roads() == null) {
                continue;
            }
            for (KakaoRoad road : section.roads()) {
                if (road == null || road.vertexes() == null) {
                    continue;
                }
                List<Double> vertexes = road.vertexes();
                for (int index = 0; index + 1 < vertexes.size(); index += 2) {
                    RouteCoordinate coordinate = new RouteCoordinate(vertexes.get(index + 1), vertexes.get(index));
                    if (geometry.isEmpty() || !geometry.getLast().equals(coordinate)) {
                        geometry.add(coordinate);
                    }
                }
            }
        }
        return List.copyOf(geometry);
    }

    private String encodeGeometry(List<RouteCoordinate> geometry) {
        if (geometry.isEmpty()) {
            return null;
        }
        return geometry.stream()
                .map(point -> String.format(Locale.ROOT, "%.6f,%.6f", point.latitude(), point.longitude()))
                .reduce((left, right) -> left + ";" + right)
                .orElse(null);
    }

    private List<RouteCoordinate> decodeGeometry(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return List.of();
        }
        List<RouteCoordinate> result = new ArrayList<>();
        for (String pair : encoded.split(";")) {
            String[] values = pair.split(",");
            if (values.length == 2) {
                try {
                    result.add(new RouteCoordinate(Double.parseDouble(values[0]), Double.parseDouble(values[1])));
                } catch (NumberFormatException ignored) {
                    return List.of();
                }
            }
        }
        return List.copyOf(result);
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
        return status == 401 || status == 403 || status == 408 || statusCode.is5xxServerError();
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record KakaoDirectionsResponse(List<KakaoRoute> routes) {
    }

    private record KakaoRoute(
            int resultCode,
            String resultMsg,
            KakaoSummary summary,
            List<KakaoSection> sections
    ) {
    }

    private record KakaoSummary(long distance, long duration) {
    }

    private record KakaoSection(List<KakaoRoad> roads) {
    }

    private record KakaoRoad(List<Double> vertexes) {
    }
}
