package com.planmate.place.service;

import com.planmate.place.dto.PlaceAutocompleteItemResponse;
import com.planmate.place.dto.PlaceAutocompleteResponse;
import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.PlaceSearchCandidate;
import com.planmate.place.dto.PlaceSearchArea;
import com.planmate.place.dto.PlaceTextSearchRequest;
import com.planmate.place.dto.PlaceTextSearchResponse;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.place.exception.InvalidPlaceIdException;
import com.planmate.place.exception.PlaceProviderUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class GooglePlacesService {

    private static final String BASE_URL = "https://places.googleapis.com/v1";
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";
    private static final String AUTOCOMPLETE_FIELD_MASK = String.join(",",
            "suggestions.placePrediction.placeId",
            "suggestions.placePrediction.text.text",
            "suggestions.placePrediction.structuredFormat.mainText.text",
            "suggestions.placePrediction.structuredFormat.secondaryText.text",
            "suggestions.placePrediction.types"
    );
    public static final String PLACE_DETAILS_FIELD_MASK = String.join(",",
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
    public static final String DESTINATION_DETAILS_FIELD_MASK = PLACE_DETAILS_FIELD_MASK;
    public static final String TEXT_SEARCH_FIELD_MASK = String.join(",",
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
    private static final String CITY_COLLECTION_TYPE = "(cities)";
    private static final String REGION_COLLECTION_TYPE = "(regions)";

    private final RestClient restClient;
    private final String apiKey;
    private final double fallbackRadiusMeters;

    public GooglePlacesService(
            RestClient.Builder restClientBuilder,
            @Value("${app.google.places.api-key:}") String apiKey,
            @Value("${app.google.places.text-search-radius-meters:30000}") double fallbackRadiusMeters
    ) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.apiKey = apiKey;
        this.fallbackRadiusMeters = fallbackRadiusMeters;
    }

    public PlaceAutocompleteResponse autocomplete(String query, String languageCode) {
        assertApiKeyConfigured();

        PlaceAutocompleteResponse cityResponse = autocomplete(query, languageCode, SearchScope.CITY);
        if (!cityResponse.items().isEmpty()) {
            return cityResponse;
        }
        return autocomplete(query, languageCode, SearchScope.REGION);
    }

    public PlaceAutocompleteResponse autocompleteAccommodation(
            String query,
            String destinationPlaceId,
            String languageCode
    ) {
        assertApiKeyConfigured();

        ResolvedDestination destination = resolveDestination(destinationPlaceId, languageCode);
        return autocomplete(query, new AutocompleteOptions(
                languageCode,
                SearchScope.ACCOMMODATION,
                destination
        ));
    }

    public PlaceAutocompleteResponse autocompleteInDestination(
            String query,
            String destinationPlaceId,
            String languageCode
    ) {
        assertApiKeyConfigured();

        ResolvedDestination destination = resolveDestination(destinationPlaceId, languageCode);
        return autocomplete(query, new AutocompleteOptions(
                languageCode,
                SearchScope.PLACE,
                destination
        ));
    }

    public ResolvedDestination resolveDestination(String placeId, String languageCode) {
        assertApiKeyConfigured();

        try {
            GooglePlaceDetailsResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/places/{placeId}");
                        if (StringUtils.hasText(languageCode)) {
                            builder.queryParam("languageCode", languageCode.trim());
                        }
                        return builder.build(placeId);
                    })
                    .headers(headers -> applyGoogleHeaders(headers, PLACE_DETAILS_FIELD_MASK))
                    .retrieve()
                    .body(GooglePlaceDetailsResponse.class);

            if (response == null || !placeId.equals(response.id())) {
                throw new InvalidPlaceIdException();
            }
            return toResolvedDestination(response);
        } catch (RestClientResponseException exception) {
            if (isInvalidPlaceIdResponse(exception.getStatusCode())) {
                throw new InvalidPlaceIdException();
            }
            throw new PlaceProviderUnavailableException(exception);
        } catch (RestClientException exception) {
            throw new PlaceProviderUnavailableException(exception);
        }
    }

    public void validatePlaceId(String placeId) {
        resolveDestination(placeId, null);
    }

    public PlaceTextSearchResponse searchText(PlaceTextSearchRequest request) {
        assertApiKeyConfigured();

        try {
            GoogleTextSearchResponse response = restClient.post()
                    .uri("/places:searchText")
                    .headers(headers -> applyGoogleHeaders(headers, TEXT_SEARCH_FIELD_MASK))
                    .body(textSearchRequestBody(request))
                    .retrieve()
                    .body(GoogleTextSearchResponse.class);

            return normalizeTextSearch(response);
        } catch (RestClientException exception) {
            throw new PlaceProviderUnavailableException(exception);
        }
    }

    private PlaceAutocompleteResponse autocomplete(String query, String languageCode, SearchScope searchScope) {
        return autocomplete(query, new AutocompleteOptions(languageCode, searchScope, null));
    }

    private PlaceAutocompleteResponse autocomplete(String query, AutocompleteOptions options) {
        try {
            GoogleAutocompleteResponse response = restClient.post()
                    .uri("/places:autocomplete")
                    .headers(headers -> applyGoogleHeaders(headers, AUTOCOMPLETE_FIELD_MASK))
                    .body(autocompleteRequestBody(query, options))
                    .retrieve()
                    .body(GoogleAutocompleteResponse.class);

            return normalizeAutocomplete(response, options.searchScope());
        } catch (RestClientException exception) {
            throw new PlaceProviderUnavailableException(exception);
        }
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new PlaceProviderUnavailableException();
        }
    }

    private void applyGoogleHeaders(HttpHeaders headers, String fieldMask) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(API_KEY_HEADER, apiKey);
        headers.set(FIELD_MASK_HEADER, fieldMask);
    }

    private Map<String, Object> autocompleteRequestBody(String query, AutocompleteOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query.trim());
        if (StringUtils.hasText(options.searchScope().includedPrimaryType())) {
            body.put("includedPrimaryTypes", List.of(options.searchScope().includedPrimaryType()));
        }
        if (StringUtils.hasText(options.languageCode())) {
            body.put("languageCode", options.languageCode().trim());
        }
        Map<String, Object> locationBias = locationBias(options.locationBiasDestination());
        if (!locationBias.isEmpty()) {
            body.put("locationBias", locationBias);
        }
        return body;
    }

    private Map<String, Object> textSearchRequestBody(PlaceTextSearchRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("textQuery", request.textQuery().trim());
        body.put("pageSize", request.pageSize());
        if (StringUtils.hasText(request.languageCode())) {
            body.put("languageCode", request.languageCode().trim());
        }
        if (StringUtils.hasText(request.pageToken())) {
            body.put("pageToken", request.pageToken().trim());
        }
        Map<String, Object> locationRestriction = locationRestriction(request.searchArea());
        if (!locationRestriction.isEmpty()) {
            body.put("locationRestriction", locationRestriction);
        } else {
            Map<String, Object> locationBias = locationBias(request.searchArea());
            if (!locationBias.isEmpty()) {
                body.put("locationBias", locationBias);
            }
        }
        return body;
    }

    private Map<String, Object> locationRestriction(PlaceSearchArea searchArea) {
        if (searchArea == null) {
            return Map.of();
        }
        if (searchArea.viewport() != null && searchArea.viewport().low() != null && searchArea.viewport().high() != null) {
            return Map.of("rectangle", Map.of(
                    "low", pointBody(searchArea.viewport().low()),
                    "high", pointBody(searchArea.viewport().high())
            ));
        }
        return Map.of();
    }

    private Map<String, Object> locationBias(PlaceSearchArea searchArea) {
        if (searchArea == null || searchArea.center() == null) {
            return Map.of();
        }
        if (searchArea.viewport() == null) {
            return Map.of("circle", Map.of(
                    "center", pointBody(searchArea.center()),
                    "radius", fallbackRadiusMeters
            ));
        }
        return Map.of();
    }

    private Map<String, Object> locationBias(ResolvedDestination destination) {
        if (destination == null) {
            return Map.of();
        }
        if (destination.viewport() != null && destination.viewport().low() != null && destination.viewport().high() != null) {
            return Map.of("rectangle", Map.of(
                    "low", pointBody(destination.viewport().low()),
                    "high", pointBody(destination.viewport().high())
            ));
        }
        if (destination.location() != null) {
            return Map.of("circle", Map.of(
                    "center", pointBody(destination.location()),
                    "radius", fallbackRadiusMeters
            ));
        }
        return Map.of();
    }

    private Map<String, Object> pointBody(GeoPoint point) {
        return Map.of(
                "latitude", point.latitude(),
                "longitude", point.longitude()
        );
    }

    private PlaceAutocompleteResponse normalizeAutocomplete(GoogleAutocompleteResponse response, SearchScope searchScope) {
        if (response == null || response.suggestions() == null) {
            return new PlaceAutocompleteResponse(List.of());
        }

        List<PlaceAutocompleteItemResponse> items = response.suggestions()
                .stream()
                .map(GoogleSuggestion::placePrediction)
                .filter(Objects::nonNull)
                .filter(prediction -> StringUtils.hasText(prediction.placeId()))
                .map(prediction -> toItemResponse(prediction, searchScope))
                .toList();

        return new PlaceAutocompleteResponse(items);
    }

    private PlaceAutocompleteItemResponse toItemResponse(GooglePlacePrediction prediction, SearchScope searchScope) {
        String displayText = textValue(prediction.text());
        String mainText = textValue(prediction.structuredFormat() == null ? null : prediction.structuredFormat().mainText());
        String secondaryText = textValue(prediction.structuredFormat() == null ? null : prediction.structuredFormat().secondaryText());

        if (!StringUtils.hasText(mainText)) {
            mainText = displayText;
        }
        if (!StringUtils.hasText(displayText)) {
            displayText = StringUtils.hasText(secondaryText) ? mainText + ", " + secondaryText : mainText;
        }

        return new PlaceAutocompleteItemResponse(
                prediction.placeId(),
                mainText,
                secondaryText,
                displayText,
                prediction.types() == null ? List.of() : List.copyOf(prediction.types()),
                searchScope.name()
        );
    }

    private ResolvedDestination toResolvedDestination(GooglePlaceDetailsResponse response) {
        String displayName = textValue(response.displayName());
        if (!StringUtils.hasText(displayName)) {
            displayName = response.formattedAddress();
        }
        if (!StringUtils.hasText(displayName)) {
            displayName = response.id();
        }

        return new ResolvedDestination(
                response.id(),
                displayName,
                response.formattedAddress(),
                toPoint(response.location()),
                toViewport(response.viewport()),
                response.types() == null ? List.of() : List.copyOf(response.types()),
                response.primaryType()
        );
    }

    private PlaceTextSearchResponse normalizeTextSearch(GoogleTextSearchResponse response) {
        if (response == null || response.places() == null) {
            return new PlaceTextSearchResponse(List.of(), null);
        }
        List<PlaceSearchCandidate> places = response.places()
                .stream()
                .filter(place -> StringUtils.hasText(place.id()))
                .map(this::toSearchCandidate)
                .toList();
        return new PlaceTextSearchResponse(places, response.nextPageToken());
    }

    private PlaceSearchCandidate toSearchCandidate(GooglePlace place) {
        return new PlaceSearchCandidate(
                place.id(),
                textValue(place.displayName()),
                place.formattedAddress(),
                toPoint(place.location()),
                place.types() == null ? List.of() : List.copyOf(place.types()),
                place.primaryType(),
                place.businessStatus(),
                place.rating(),
                place.userRatingCount(),
                place.regularOpeningHours() == null || place.regularOpeningHours().weekdayDescriptions() == null
                        ? List.of()
                        : List.copyOf(place.regularOpeningHours().weekdayDescriptions())
        );
    }

    private GeoPoint toPoint(GoogleLocation location) {
        if (location == null || location.latitude() == null || location.longitude() == null) {
            return null;
        }
        return new GeoPoint(location.latitude(), location.longitude());
    }

    private GeoViewport toViewport(GoogleViewport viewport) {
        if (viewport == null) {
            return null;
        }
        GeoPoint low = toPoint(viewport.low());
        GeoPoint high = toPoint(viewport.high());
        if (low == null || high == null) {
            return null;
        }
        return new GeoViewport(low, high);
    }

    private String textValue(GoogleText text) {
        return text == null || text.text() == null ? "" : text.text();
    }

    private boolean isInvalidPlaceIdResponse(HttpStatusCode statusCode) {
        return statusCode.value() == 400 || statusCode.value() == 404;
    }

    private record GoogleAutocompleteResponse(
            List<GoogleSuggestion> suggestions
    ) {
    }

    private record GoogleSuggestion(
            GooglePlacePrediction placePrediction
    ) {
    }

    private record GooglePlacePrediction(
            String placeId,
            GoogleText text,
            GoogleStructuredFormat structuredFormat,
            List<String> types
    ) {
    }

    private record GoogleStructuredFormat(
            GoogleText mainText,
            GoogleText secondaryText
    ) {
    }

    private record GoogleText(
            String text
    ) {
    }

    public double fallbackRadiusMeters() {
        return fallbackRadiusMeters;
    }

    private record GooglePlaceDetailsResponse(
            String id,
            GoogleText displayName,
            String formattedAddress,
            GoogleLocation location,
            GoogleViewport viewport,
            List<String> types,
            String primaryType
    ) {
    }

    private record GoogleTextSearchResponse(
            List<GooglePlace> places,
            String nextPageToken
    ) {
    }

    private record GooglePlace(
            String id,
            GoogleText displayName,
            String formattedAddress,
            GoogleLocation location,
            List<String> types,
            String primaryType,
            String businessStatus,
            Double rating,
            Integer userRatingCount,
            GoogleOpeningHours regularOpeningHours
    ) {
    }

    private record GoogleLocation(
            Double latitude,
            Double longitude
    ) {
    }

    private record GoogleViewport(
            GoogleLocation low,
            GoogleLocation high
    ) {
    }

    private record GoogleOpeningHours(
            List<String> weekdayDescriptions
    ) {
    }

    private enum SearchScope {
        CITY(CITY_COLLECTION_TYPE),
        REGION(REGION_COLLECTION_TYPE),
        ACCOMMODATION(null),
        PLACE(null);

        private final String includedPrimaryType;

        SearchScope(String includedPrimaryType) {
            this.includedPrimaryType = includedPrimaryType;
        }

        private String includedPrimaryType() {
            return includedPrimaryType;
        }
    }

    private record AutocompleteOptions(
            String languageCode,
            SearchScope searchScope,
            ResolvedDestination locationBiasDestination
    ) {
    }

}
