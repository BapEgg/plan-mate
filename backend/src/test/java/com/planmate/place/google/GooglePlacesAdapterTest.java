package com.planmate.place.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.GeoViewport;
import com.planmate.place.api.PlaceAutocompleteResult;
import com.planmate.place.api.PlaceSearchArea;
import com.planmate.place.api.PlaceTextSearchQuery;
import com.planmate.place.api.PlaceTextSearchResult;
import com.planmate.place.api.ResolvedPlace;
import com.planmate.place.api.exception.InvalidPlaceIdException;
import com.planmate.place.api.exception.PlaceProviderConfigurationException;
import com.planmate.place.api.exception.PlaceProviderRequestRejectedException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

class GooglePlacesAdapterTest {

    private static final String PLACE_DETAILS_FIELD_MASK = String.join(",",
            "id",
            "displayName.text",
            "formattedAddress",
            "location.latitude",
            "location.longitude",
            "viewport.low.latitude",
            "viewport.low.longitude",
            "viewport.high.latitude",
            "viewport.high.longitude",
            "types",
            "primaryType"
    );
    private static final String TEXT_SEARCH_FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName.text",
            "places.formattedAddress",
            "places.location.latitude",
            "places.location.longitude",
            "places.types",
            "places.primaryType",
            "places.businessStatus",
            "places.rating",
            "places.userRatingCount",
            "places.regularOpeningHours.weekdayDescriptions",
            "nextPageToken"
    );

    @Test
    void resolveReturnsInternalDto() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/place-kyoto")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-FieldMask", PLACE_DETAILS_FIELD_MASK))
                .andRespond(withSuccess(fixture("google/place-details-kyoto.json"), MediaType.APPLICATION_JSON));

        ResolvedPlace destination = service.resolve("place-kyoto", "ko");

        assertThat(destination.placeId()).isEqualTo("place-kyoto");
        assertThat(destination.displayName()).isEqualTo("Kyoto");
        assertThat(destination.location().latitude()).isEqualTo(35.0116);
        assertThat(destination.viewport()).isNotNull();
        server.verify();
    }

    @Test
    void resolveRejectsInvalidPlaceId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/bad-place")))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> service.resolve("bad-place", "ko"))
                .isInstanceOf(InvalidPlaceIdException.class);
        server.verify();
    }

    @Test
    void searchTextSendsLocationRestrictionAndReturnsCandidates() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);
        ResolvedPlace destination = new ResolvedPlace(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0116, 135.7681),
                new GeoViewport(new GeoPoint(34.8, 135.5), new GeoPoint(35.2, 136.0)),
                List.of("locality"),
                "locality"
        );

        server.expect(requestTo(containsString("/places:searchText")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-FieldMask", TEXT_SEARCH_FIELD_MASK))
                .andExpect(jsonPath("$.textQuery").value("Kyoto popular attractions"))
                .andExpect(jsonPath("$.locationRestriction.rectangle.low.latitude").value(34.8))
                .andRespond(withSuccess(fixture("google/text-search-kyoto.json"), MediaType.APPLICATION_JSON));

        PlaceTextSearchResult response = service.searchText(new PlaceTextSearchQuery(
                "Kyoto popular attractions",
                "ko",
                20,
                PlaceSearchArea.of(destination.location(), destination.viewport()),
                null
        ));

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().getFirst().placeId()).isEqualTo("candidate-1");
        assertThat(response.nextPageToken()).isEqualTo("next-token");
        server.verify();
    }

    @Test
    void searchTextUsesCircleLocationBiasWhenViewportIsMissing() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places:searchText")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.textQuery").value("Kyoto popular attractions"))
                .andExpect(jsonPath("$.locationRestriction").doesNotExist())
                .andExpect(jsonPath("$.locationBias.circle.center.latitude").value(33.5902))
                .andExpect(jsonPath("$.locationBias.circle.center.longitude").value(130.4206))
                .andExpect(jsonPath("$.locationBias.circle.radius").value(30000.0))
                .andRespond(withSuccess(fixture("google/text-search-kyoto.json"), MediaType.APPLICATION_JSON));

        PlaceTextSearchResult response = service.searchText(new PlaceTextSearchQuery(
                "Kyoto popular attractions",
                "ko",
                20,
                PlaceSearchArea.circle(new GeoPoint(33.5902, 130.4206)),
                null
        ));

        assertThat(response.places()).hasSize(1);
        server.verify();
    }

    @Test
    void autocompleteAccommodationSendsRectangleLocationBiasWithoutPrimaryTypeFilter() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/place-kyoto")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture("google/place-details-kyoto.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/places:autocomplete")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.input").value("Dormy Inn"))
                .andExpect(jsonPath("$.languageCode").value("ko"))
                .andExpect(jsonPath("$.includedPrimaryTypes").doesNotExist())
                .andExpect(jsonPath("$.locationBias.rectangle.low.latitude").value(34.8))
                .andRespond(withSuccess(autocompleteFixture(), MediaType.APPLICATION_JSON));

        PlaceAutocompleteResult response = service.autocompleteAccommodation(
                "Dormy Inn",
                "place-kyoto",
                "ko"
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().placeId()).isEqualTo("accommodation-1");
        assertThat(response.items().getFirst().searchScope()).isEqualTo("ACCOMMODATION");
        server.verify();
    }

    @Test
    void autocompleteInDestinationSendsRectangleLocationBiasWithoutPrimaryTypeFilter() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/place-kyoto")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture("google/place-details-kyoto.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/places:autocomplete")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.input").value("Fushimi Inari"))
                .andExpect(jsonPath("$.languageCode").value("ko"))
                .andExpect(jsonPath("$.includedPrimaryTypes").doesNotExist())
                .andExpect(jsonPath("$.locationBias.rectangle.low.latitude").value(34.8))
                .andRespond(withSuccess(autocompleteFixture(), MediaType.APPLICATION_JSON));

        PlaceAutocompleteResult response = service.autocompleteInDestination(
                "Fushimi Inari",
                "place-kyoto",
                "ko"
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().placeId()).isEqualTo("accommodation-1");
        assertThat(response.items().getFirst().searchScope()).isEqualTo("PLACE");
        server.verify();
    }

    @Test
    void autocompleteAccommodationUsesCircleBiasWhenDestinationHasNoViewport() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/place-without-viewport")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(placeDetailsWithoutViewportFixture(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/places:autocomplete")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.locationBias.circle.center.latitude").value(35.0))
                .andExpect(jsonPath("$.locationBias.circle.radius").value(30000.0))
                .andExpect(jsonPath("$.includedPrimaryTypes").doesNotExist())
                .andRespond(withSuccess(autocompleteFixture(), MediaType.APPLICATION_JSON));

        service.autocompleteAccommodation("Dormy Inn", "place-without-viewport", "ko");

        server.verify();
    }

    @Test
    void autocompleteAccommodationKeepsProviderUnavailablePolicy() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/place-kyoto")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.autocompleteAccommodation("Dormy Inn", "place-kyoto", "ko"))
                .isInstanceOf(PlaceProviderUnavailableException.class);
        server.verify();
    }

    @Test
    void searchTextClassifiesRateLimitAndServerErrorsAsProviderUnavailable() {
        for (HttpStatus status : List.of(
                HttpStatus.REQUEST_TIMEOUT,
                HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR
        )) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);
            server.expect(requestTo(containsString("/places:searchText")))
                    .andRespond(withStatus(status));

            assertThatThrownBy(() -> service.searchText(textSearchQuery()))
                    .isInstanceOf(PlaceProviderUnavailableException.class);
            server.verify();
        }
    }

    @Test
    void searchTextClassifiesNonRateLimitClientErrorAsRejectedRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesAdapter service = new GooglePlacesAdapter(builder, "test-key", 30000);
        server.expect(requestTo(containsString("/places:searchText")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.searchText(textSearchQuery()))
                .isInstanceOf(PlaceProviderRequestRejectedException.class);
        server.verify();
    }

    @Test
    void searchTextClassifiesMissingApiKeyAsConfigurationFailure() {
        GooglePlacesAdapter service = new GooglePlacesAdapter(RestClient.builder(), " ", 30000);

        assertThatThrownBy(() -> service.searchText(textSearchQuery()))
                .isInstanceOf(PlaceProviderConfigurationException.class);
    }

    private PlaceTextSearchQuery textSearchQuery() {
        return new PlaceTextSearchQuery(
                "Kyoto attractions",
                "ko",
                20,
                PlaceSearchArea.circle(new GeoPoint(35.0, 135.0)),
                null
        );
    }

    private String fixture(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String autocompleteFixture() {
        return """
                {
                  "suggestions": [
                    {
                      "placePrediction": {
                        "placeId": "accommodation-1",
                        "text": { "text": "Dormy Inn Kyoto, Kyoto, Japan" },
                        "structuredFormat": {
                          "mainText": { "text": "Dormy Inn Kyoto" },
                          "secondaryText": { "text": "Kyoto, Japan" }
                        },
                        "types": ["lodging", "point_of_interest", "establishment"]
                      }
                    }
                  ]
                }
                """;
    }

    private String placeDetailsWithoutViewportFixture() {
        return """
                {
                  "id": "place-without-viewport",
                  "displayName": { "text": "No Viewport City" },
                  "formattedAddress": "No Viewport Address",
                  "location": {
                    "latitude": 35.0,
                    "longitude": 135.0
                  },
                  "types": ["locality"],
                  "primaryType": "locality"
                }
                """;
    }
}
