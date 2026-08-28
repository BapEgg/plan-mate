package com.planmate.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceApiDtoTest {

    @Test
    void listFieldsUseEmptyImmutableCopies() {
        List<String> types = new ArrayList<>(List.of("museum"));
        List<String> openingPeriods = new ArrayList<>(List.of("Mon 09:00-18:00"));
        PlaceSearchCandidate candidate = new PlaceSearchCandidate(
                "place-1",
                "Museum",
                "address",
                new GeoPoint(35.0, 135.0),
                types,
                "museum",
                "OPERATIONAL",
                4.5,
                100,
                openingPeriods
        );

        types.add("tourist_attraction");
        openingPeriods.add("Tue 09:00-18:00");

        assertThat(candidate.types()).containsExactly("museum");
        assertThat(candidate.openingPeriods()).containsExactly("Mon 09:00-18:00");
        assertThatThrownBy(() -> candidate.types().add("park"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new PlaceSearchCandidate(
                "place-2",
                "Park",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ).types()).isEmpty();
    }

    @Test
    void autocompleteAndSearchResultsUseImmutableLists() {
        List<PlaceAutocompleteItem> autocompleteItems = new ArrayList<>(List.of(new PlaceAutocompleteItem(
                "place-1",
                "Main",
                "Secondary",
                "Display",
                List.of("locality"),
                "CITY"
        )));
        List<PlaceSearchCandidate> searchPlaces = new ArrayList<>();

        PlaceAutocompleteResult autocompleteResult = new PlaceAutocompleteResult(autocompleteItems);
        PlaceTextSearchResult searchResult = new PlaceTextSearchResult(searchPlaces, "next-token");

        autocompleteItems.clear();
        searchPlaces.add(new PlaceSearchCandidate(
                "place-2",
                "Candidate",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(autocompleteResult.items()).hasSize(1);
        assertThat(searchResult.places()).isEmpty();
        assertThatThrownBy(() -> autocompleteResult.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resolvedPlaceUsesImmutableTypes() {
        List<String> types = new ArrayList<>(List.of("locality"));

        ResolvedPlace place = new ResolvedPlace(
                "place-1",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0, 135.0),
                null,
                types,
                "locality"
        );

        types.add("political");

        assertThat(place.types()).containsExactly("locality");
        assertThatThrownBy(() -> place.types().add("political"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
