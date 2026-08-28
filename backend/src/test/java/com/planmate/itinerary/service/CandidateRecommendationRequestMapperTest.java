package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.Interest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateRecommendationRequestMapperTest {

    private final CandidateRecommendationRequestMapper mapper = new CandidateRecommendationRequestMapper();

    @Test
    void mapsResolvedDestinationAndCompleteViewport() {
        CandidateRecommendationRequest request = mapper.map(snapshot(
                destination(35.0, 135.0, new GenerationInputSnapshot.Viewport(34.8, 134.8, 35.2, 135.2)),
                preference(List.of("FOOD")),
                accommodation("UNDECIDED", null, null),
                List.of()
        ));

        assertThat(request.destination().displayName()).isEqualTo("Kyoto");
        assertThat(request.destination().location().latitude()).isEqualTo(35.0);
        assertThat(request.destination().location().longitude()).isEqualTo(135.0);
        assertThat(request.destination().viewport().low().latitude()).isEqualTo(34.8);
        assertThat(request.destination().viewport().high().longitude()).isEqualTo(135.2);
    }

    @Test
    void mapsPartialViewportToNull() {
        CandidateRecommendationRequest request = mapper.map(snapshot(
                destination(35.0, 135.0, new GenerationInputSnapshot.Viewport(34.8, null, 35.2, 135.2)),
                preference(List.of("FOOD")),
                accommodation("UNDECIDED", null, null),
                List.of()
        ));

        assertThat(request.destination().viewport()).isNull();
    }

    @Test
    void rejectsUnresolvedDestination() {
        assertThatThrownBy(() -> mapper.map(snapshot(
                destination(null, 135.0, null),
                preference(List.of("FOOD")),
                accommodation("UNDECIDED", null, null),
                List.of()
        )))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Trip destination has not been resolved.");
    }

    @Test
    void mapsInterestsWithTrimUppercaseDedupAndUnknownIgnored() {
        CandidateRecommendationRequest request = mapper.map(snapshot(
                destination(35.0, 135.0, null),
                preference(List.of(" food ", "unknown", "CAFE", "food", "", "night_view")),
                accommodation("UNDECIDED", null, null),
                List.of()
        ));

        assertThat(request.interests()).containsExactly(Interest.FOOD, Interest.CAFE, Interest.NIGHT_VIEW);
    }

    @Test
    void mapsAccommodationOnlyForPlaceSearchWithLocation() {
        CandidateRecommendationRequest request = mapper.map(snapshot(
                destination(35.0, 135.0, null),
                preference(List.of()),
                accommodation(" place_search ", 35.1, 135.1),
                List.of()
        ));

        assertThat(request.accommodation()).isNotNull();
        assertThat(request.accommodation().location().latitude()).isEqualTo(35.1);
        assertThat(request.accommodation().location().longitude()).isEqualTo(135.1);
    }

    @Test
    void ignoresAccommodationWhenModeDoesNotMatchOrLocationIsMissing() {
        CandidateRecommendationRequest undecided = mapper.map(snapshot(
                destination(35.0, 135.0, null),
                preference(List.of()),
                accommodation("UNDECIDED", 35.1, 135.1),
                List.of()
        ));
        CandidateRecommendationRequest missingLocation = mapper.map(snapshot(
                destination(35.0, 135.0, null),
                preference(List.of()),
                accommodation("PLACE_SEARCH", 35.1, null),
                List.of()
        ));

        assertThat(undecided.accommodation()).isNull();
        assertThat(missingLocation.accommodation()).isNull();
    }

    @Test
    void mapsResolvedAndUnresolvedMustVisitPlaces() {
        CandidateRecommendationRequest request = mapper.map(snapshot(
                destination(35.0, 135.0, null),
                preference(List.of()),
                accommodation("UNDECIDED", null, null),
                List.of(
                        mustVisitPlace("must-1", 35.1, 135.1),
                        mustVisitPlace("must-2", null, 135.2)
                )
        ));

        assertThat(request.mustVisitPlaces()).hasSize(2);
        assertThat(request.mustVisitPlaces().get(0).location()).isNotNull();
        assertThat(request.mustVisitPlaces().get(0).isResolved()).isTrue();
        assertThat(request.mustVisitPlaces().get(1).location()).isNull();
        assertThat(request.mustVisitPlaces().get(1).isResolved()).isFalse();
    }

    private GenerationInputSnapshot snapshot(
            GenerationInputSnapshot.Destination destination,
            GenerationInputSnapshot.Preference preference,
            GenerationInputSnapshot.Accommodation accommodation,
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces
    ) {
        return new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                destination,
                new GenerationInputSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                preference,
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                accommodation,
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                mustVisitPlaces,
                List.of(),
                null
        );
    }

    private GenerationInputSnapshot.Destination destination(
            Double latitude,
            Double longitude,
            GenerationInputSnapshot.Viewport viewport
    ) {
        return new GenerationInputSnapshot.Destination(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                latitude,
                longitude,
                viewport,
                List.of("locality"),
                "locality"
        );
    }

    private GenerationInputSnapshot.Preference preference(List<String> interests) {
        return new GenerationInputSnapshot.Preference("BALANCED", interests);
    }

    private GenerationInputSnapshot.Accommodation accommodation(
            String mode,
            Double latitude,
            Double longitude
    ) {
        return new GenerationInputSnapshot.Accommodation(
                mode,
                null,
                "hotel-place",
                "Hotel",
                "Hotel address",
                latitude,
                longitude,
                List.of("lodging"),
                "lodging",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
        );
    }

    private GenerationInputSnapshot.MustVisitPlace mustVisitPlace(
            String placeId,
            Double latitude,
            Double longitude
    ) {
        return new GenerationInputSnapshot.MustVisitPlace(
                placeId,
                "Must " + placeId,
                "Must address",
                latitude,
                longitude,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }
}
