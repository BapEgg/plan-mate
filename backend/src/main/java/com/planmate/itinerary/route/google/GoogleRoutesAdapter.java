package com.planmate.itinerary.route.google;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteTravelTimePort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GoogleRoutesAdapter implements RouteTravelTimePort {

    private static final String BASE_URL = "https://routes.googleapis.com";
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";
    private static final String FIELD_MASK = "routes.duration,routes.distanceMeters";

    private final RestClient restClient;
    private final String apiKey;

    public GoogleRoutesAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.google.routes.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    @Override
    public Optional<RouteTravelTime> findRoute(
            RoutePoint origin,
            RoutePoint destination,
            TravelMode travelMode
    ) {
        assertApiKeyConfigured();
        try {
            GoogleRoutesResponse response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .headers(this::applyGoogleHeaders)
                    .body(requestBody(origin, destination, travelMode))
                    .retrieve()
                    .body(GoogleRoutesResponse.class);
            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                return Optional.empty();
            }
            GoogleRoute route = response.routes().getFirst();
            return Optional.of(new RouteTravelTime(
                    parseDuration(route.duration()),
                    route.distanceMeters()
            ));
        } catch (RestClientResponseException exception) {
            if (isProviderUnavailable(exception.getStatusCode())) {
                throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
            }
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_REQUEST_FAILED, exception);
        } catch (RestClientException exception) {
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE, exception);
        } catch (RuntimeException exception) {
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_REQUEST_FAILED, exception);
        }
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ItineraryException(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
        }
    }

    private void applyGoogleHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(API_KEY_HEADER, apiKey);
        headers.set(FIELD_MASK_HEADER, FIELD_MASK);
    }

    private Map<String, Object> requestBody(
            RoutePoint origin,
            RoutePoint destination,
            TravelMode travelMode
    ) {
        return Map.of(
                "origin", waypoint(origin),
                "destination", waypoint(destination),
                "travelMode", travelMode.name()
        );
    }

    private Map<String, Object> waypoint(RoutePoint point) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", point.latitude(),
                "longitude", point.longitude()
        )));
    }

    private Duration parseDuration(String value) {
        if (!StringUtils.hasText(value) || !value.endsWith("s")) {
            throw new IllegalArgumentException("Google Routes duration is invalid.");
        }
        BigDecimal seconds = new BigDecimal(value.substring(0, value.length() - 1));
        long wholeSeconds = seconds.longValue();
        int nanos = seconds.subtract(BigDecimal.valueOf(wholeSeconds))
                .movePointRight(9)
                .intValueExact();
        return Duration.ofSeconds(wholeSeconds, nanos);
    }

    private boolean isProviderUnavailable(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return status == 401
                || status == 403
                || status == 408
                || status == 429
                || statusCode.is5xxServerError();
    }

    private record GoogleRoutesResponse(List<GoogleRoute> routes) {
    }

    private record GoogleRoute(String duration, long distanceMeters) {
    }
}
