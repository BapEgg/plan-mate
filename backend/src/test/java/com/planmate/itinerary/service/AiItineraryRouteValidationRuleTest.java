package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.route.RouteTravelTimePort;
import com.planmate.itinerary.route.RouteTravelTimePort.RouteTravelTime;
import com.planmate.itinerary.route.RouteTravelTimePort.TravelMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

class AiItineraryRouteValidationRuleTest {

    private final RouteTravelTimePort port = Mockito.mock(RouteTravelTimePort.class);
    private final AiItineraryRouteValidationRule rule = new AiItineraryRouteValidationRule(port);

    @ParameterizedTest
    @CsvSource({
            "WALK,WALK",
            "BIKE,BICYCLE",
            "RENTAL_CAR,DRIVE",
            "TAXI,DRIVE",
            "TOUR,DRIVE"
    })
    void mapsPrimaryTransportMode(String primaryMode, TravelMode expectedMode) {
        when(port.findRoute(any(), any(), any())).thenReturn(Optional.of(route(600)));

        AiItineraryRouteValidationRule.Result result = rule.validate(
                input(primaryMode), candidates(), adjacentItems(600)
        );

        assertThat(result.errors()).isEmpty();
        verify(port).findRoute(any(), any(), Mockito.eq(expectedMode));
    }

    @Test
    void acceptsRouteDurationShorterThanOrEqualToGap() {
        when(port.findRoute(any(), any(), any()))
                .thenReturn(Optional.of(route(599)), Optional.of(route(600)));

        assertThat(rule.validate(input("WALK"), candidates(), adjacentItems(600)).errors()).isEmpty();
        assertThat(rule.validate(input("WALK"), candidates(), adjacentItems(600)).errors()).isEmpty();
    }

    @Test
    void reportsInsufficientTravelTimeWithPreviousRelatedTarget() {
        when(port.findRoute(any(), any(), any())).thenReturn(Optional.of(route(601)));

        List<ValidationIssue> errors = rule.validate(
                input("WALK"), candidates(), adjacentItems(600)
        ).errors();

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().code()).isEqualTo(ValidationIssueCode.INSUFFICIENT_TRAVEL_TIME);
        assertThat(errors.getFirst().message()).contains("600 seconds", "601 seconds");
        assertThat(errors.getFirst().path()).isEqualTo("days[0].items[1].startTime");
        assertThat(errors.getFirst().relatedTargets()).extracting(target -> target.path())
                .containsExactly("days[0].items[0]");
    }

    @Test
    void reportsEmptyRouteAsRouteNotFound() {
        when(port.findRoute(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(rule.validate(input("WALK"), candidates(), adjacentItems(600)).errors())
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.ADJACENT_ROUTE_NOT_FOUND);
    }

    @Test
    void skipsSamePlaceAndPairsAcrossDays() {
        List<ValidatedDraftItem> items = List.of(
                item(0, 0, 1, 1, "place-1", 540, 600),
                item(0, 1, 1, 2, "place-1", 660, 720),
                item(1, 0, 2, 1, "place-2", 540, 600)
        );

        assertThat(rule.validate(input("WALK"), candidates(), items).errors()).isEmpty();
        verify(port, never()).findRoute(any(), any(), any());
    }

    @Test
    void reportsSequenceTimeOrderBeforeCallingProvider() {
        List<ValidatedDraftItem> items = List.of(
                item(0, 0, 1, 1, "place-1", 840, 900),
                item(0, 1, 1, 2, "place-2", 600, 660)
        );

        assertThat(rule.validate(input("WALK"), candidates(), items).errors())
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.ITEM_SEQUENCE_TIME_ORDER_INVALID);
        verify(port, never()).findRoute(any(), any(), any());
    }

    @Test
    void publicTransitCreatesOneUnverifiedIssueWithoutProviderCall() {
        List<ValidatedDraftItem> threeItems = List.of(
                item(0, 0, 1, 1, "place-1", 540, 600),
                item(0, 1, 1, 2, "place-2", 660, 720),
                item(0, 2, 1, 3, "place-3", 780, 840)
        );

        AiItineraryRouteValidationRule.Result result = rule.validate(
                input("PUBLIC_TRANSIT"), List.of(candidate(1, "place-1"), candidate(2, "place-2"), candidate(3, "place-3")), threeItems
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.unverifiedConditions()).hasSize(1);
        assertThat(result.unverifiedConditions().getFirst().code())
                .isEqualTo(ValidationIssueCode.ADJACENT_ROUTE_NOT_VERIFIED);
        assertThat(result.unverifiedConditions().getFirst().path()).isEqualTo("transportation.primaryMode");
        verify(port, never()).findRoute(any(), any(), any());
    }

    private RouteTravelTime route(long seconds) {
        return new RouteTravelTime(Duration.ofSeconds(seconds), 1_000);
    }

    private List<ValidatedDraftItem> adjacentItems(long gapSeconds) {
        long nextStart = 600 + gapSeconds / 60;
        return List.of(
                item(0, 0, 1, 1, "place-1", 540, 600),
                item(0, 1, 1, 2, "place-2", nextStart, nextStart + 60)
        );
    }

    private ValidatedDraftItem item(
            int dayIndex, int itemIndex, int day, int sequence, String placeId, long start, long end
    ) {
        return new ValidatedDraftItem(
                "days[" + dayIndex + "].items[" + itemIndex + "]",
                dayIndex, itemIndex, day, sequence, placeId,
                LocalTime.of((int) (start / 60), (int) (start % 60)), start, end, (int) (end - start)
        );
    }

    private List<GenerationCandidateSnapshot> candidates() {
        return List.of(candidate(1, "place-1"), candidate(2, "place-2"));
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId) {
        return new GenerationCandidateSnapshot(
                rank, placeId, "Place", "Address",
                new GenerationCandidateSnapshot.Location(35.0 + rank, 135.0 + rank),
                "tourist_attraction", List.of("tourist_attraction"), "OPERATIONAL",
                4.5, 100, List.of(), List.of(), false, 100.0, 42.5
        );
    }

    private GenerationInputSnapshot input(String primaryMode) {
        return new GenerationInputSnapshot(
                1L, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9),
                null, null, null, null,
                new GenerationInputSnapshot.Transportation(primaryMode, List.of()),
                null, LocalTime.of(8, 0), LocalTime.of(20, 0),
                List.of(), List.of(), null
        );
    }
}
