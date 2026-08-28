package com.planmate.itinerary.route.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.RouteTravelTimePort.TravelMode;
import java.time.Duration;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleRoutesAdapterTest {

    @Test
    void sendsCoordinatesModeAndHeadersAndParsesRoute() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesAdapter adapter = new GoogleRoutesAdapter(builder, "test-key");
        server.expect(requestTo(containsString("/directions/v2:computeRoutes")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters"))
                .andExpect(jsonPath("$.travelMode").value("WALK"))
                .andExpect(jsonPath("$.origin.location.latLng.latitude").value(35.0))
                .andExpect(jsonPath("$.destination.location.latLng.longitude").value(136.0))
                .andRespond(withSuccess(
                        "{\"routes\":[{\"duration\":\"123.5s\",\"distanceMeters\":456}]}",
                        MediaType.APPLICATION_JSON
                ));

        var route = adapter.findRoute(
                new RoutePoint(35.0, 135.0), new RoutePoint(36.0, 136.0), TravelMode.WALK
        );

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().duration()).isEqualTo(Duration.ofMillis(123_500));
        assertThat(route.orElseThrow().distanceMeters()).isEqualTo(456);
        server.verify();
    }

    @Test
    void emptyRoutesReturnsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesAdapter adapter = new GoogleRoutesAdapter(builder, "test-key");
        server.expect(requestTo(containsString("/directions/v2:computeRoutes")))
                .andRespond(withSuccess("{\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThat(adapter.findRoute(
                new RoutePoint(35.0, 135.0), new RoutePoint(36.0, 136.0), TravelMode.DRIVE
        )).isEmpty();
        server.verify();
    }

    @Test
    void providerFailuresBecomeServiceUnavailable() {
        assertProviderUnavailable(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertProviderUnavailable(withServerError());
    }

    @Test
    void badRequestBecomesIntegrationError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesAdapter adapter = new GoogleRoutesAdapter(builder, "test-key");
        server.expect(requestTo(containsString("/directions/v2:computeRoutes")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.findRoute(
                new RoutePoint(35.0, 135.0), new RoutePoint(36.0, 136.0), TravelMode.DRIVE
        )).isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_REQUEST_FAILED.code()));
    }

    @Test
    void missingApiKeyBecomesServiceUnavailableWithoutRequest() {
        GoogleRoutesAdapter adapter = new GoogleRoutesAdapter(RestClient.builder(), " ");

        assertThatThrownBy(() -> adapter.findRoute(
                new RoutePoint(35.0, 135.0), new RoutePoint(36.0, 136.0), TravelMode.WALK
        )).isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE.code()));
    }

    @Test
    void networkFailureBecomesServiceUnavailable() {
        RestClient.Builder builder = RestClient.builder().requestFactory((uri, method) -> {
            throw new IOException("network unavailable");
        });
        GoogleRoutesAdapter adapter = new GoogleRoutesAdapter(builder, "test-key");

        assertThatThrownBy(() -> adapter.findRoute(
                new RoutePoint(35.0, 135.0), new RoutePoint(36.0, 136.0), TravelMode.WALK
        )).isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE.code()));
    }

    private void assertProviderUnavailable(
            org.springframework.test.web.client.response.DefaultResponseCreator response
    ) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesAdapter adapter = new GoogleRoutesAdapter(builder, "test-key");
        server.expect(requestTo(containsString("/directions/v2:computeRoutes"))).andRespond(response);

        assertThatThrownBy(() -> adapter.findRoute(
                new RoutePoint(35.0, 135.0), new RoutePoint(36.0, 136.0), TravelMode.BICYCLE
        )).isInstanceOf(ItineraryException.class)
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.ROUTE_PROVIDER_UNAVAILABLE.code()));
        server.verify();
    }
}
