package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteTravelTimePort;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiItineraryDraftValidationServiceTest {

    private final AiItineraryDraftValidationService validationService =
            new AiItineraryDraftValidationService(
                    new AiItineraryTimeValidationRule(),
                    new AiItineraryAvoidConditionValidationRule(),
                    new AiItineraryRepeatedPlaceValidationRule(),
                    new AiItineraryRouteValidationRule(successfulRoutePort())
            );

    private static RouteTravelTimePort successfulRoutePort() {
        return (origin, destination, travelMode) -> Optional.of(
                new RouteTravelTimePort.RouteTravelTime(Duration.ZERO, 0)
        );
    }

    @Test
    void acceptsV1DraftWithoutCandidateSnapshotsAndWithoutWhitelist() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"))),
                List.of(),
                draft("place-1", "outside-v1-candidate")
        );

        assertThat(report.canPersist()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void appliesRepeatedPlaceWarningButSkipsAvoidConditionsForV1() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(), List.of("SHOPPING", "LONG_WALK")),
                List.of(),
                draft(" place-1 ", "place-1")
        );

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.REPEATED_PLACE);
        assertThat(report.unverifiedConditions()).isEmpty();
        assertThat(report.canPersist()).isTrue();
    }

    @Test
    void collectsMissingV1MustVisitPlacesInInputOrder() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-3"), mustVisitPlace("place-4"))),
                List.of(),
                draft("place-1", "place-2")
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.REQUIRED_PLACE_MISSING,
                        ValidationIssueCode.REQUIRED_PLACE_MISSING
                );
        assertThat(report.errors())
                .extracting(ValidationIssue::placeId)
                .containsExactly("place-3", "place-4");
    }

    @Test
    void acceptsV2DraftWhenEveryPlaceIdIsAllowedAndForcedCandidatesAreIncluded() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of(mustVisitPlace("not-a-v2-rule"))),
                List.of(candidate(1, "place-1", true), candidate(2, "place-2", false)),
                draft("place-1", "place-2")
        );

        assertThat(report.hasErrors()).isFalse();
        assertThat(report.warnings()).isEmpty();
        assertThat(report.unverifiedConditions()).isEmpty();
    }

    @Test
    void forcedMustVisitRepeatedPlaceStillCreatesWarning() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", true)),
                draft("place-1", "place-1")
        );

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.REPEATED_PLACE);
        assertThat(report.canPersist()).isTrue();
    }

    @Test
    void collectsV2AvoidErrorsAfterTimeErrorsAndUnverifiedConditionsSeparately() {
        GenerationCandidateSnapshot shopping = new GenerationCandidateSnapshot(
                1, "place-1", "Mall", "Address",
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                "shopping_mall", List.of("shopping_mall"), "OPERATIONAL",
                4.5, 100, List.of(), List.of(), false, 100.0, 42.5
        );
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of(), List.of("SHOPPING", "LONG_WALK")),
                List.of(shopping, candidate(2, "place-2", false)),
                new AiItineraryDraft("10", List.of(
                        day(1, item(1, "place-1", "07:00", 60)),
                        day(2, item(1, "place-2"))
                ))
        );

        assertThat(report.errors()).extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.OUTSIDE_DAILY_WINDOW,
                        ValidationIssueCode.AVOID_CONDITION_VIOLATED
                );
        assertThat(report.errors().get(1).condition()).isEqualTo("SHOPPING");
        assertThat(report.unverifiedConditions()).extracting(ValidationIssue::condition)
                .containsExactly("LONG_WALK");
    }

    @Test
    void collectsEveryV2WhitelistViolationInItemOrder() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", false)),
                new AiItineraryDraft("10", List.of(
                        day(1, item(1, "outside-1")),
                        day(2, item(1, "outside-2"))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED
                );
        assertThat(report.errors())
                .extracting(ValidationIssue::path)
                .containsExactly("days[0].items[0].placeId", "days[1].items[0].placeId");
        assertThat(report.errors())
                .extracting(ValidationIssue::placeId)
                .containsExactly("outside-1", "outside-2");
    }

    @Test
    void trimsPlaceIdsBeforeV2WhitelistComparisonAndKeepsCaseSensitive() {
        AiItineraryValidationReport accepted = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, " place-1 ", true), candidate(2, "place-2", false)),
                draft(" place-1 ", "place-2")
        );
        AiItineraryValidationReport rejected = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", false)),
                draft("PLACE-1", "place-2")
        );

        assertThat(accepted.errors()).isEmpty();
        assertThat(rejected.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.CANDIDATE_NOT_ALLOWED);
    }

    @Test
    void collectsMissingForcedCandidatesInRankOrder() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(
                        candidate(2, "place-2", true),
                        candidate(1, "place-1", true),
                        candidate(3, "place-3", false)
                ),
                draft("place-3", "place-3")
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.REQUIRED_PLACE_MISSING,
                        ValidationIssueCode.REQUIRED_PLACE_MISSING
                );
        assertThat(report.errors())
                .extracting(ValidationIssue::placeId)
                .containsExactly("place-1", "place-2");
    }

    @Test
    void rejectsV2DraftWhenCandidateSnapshotsAreMissingAsInvariant() {
        assertThatThrownBy(() -> validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(),
                draft("place-1", "place-2")
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation candidate snapshots not found.")
                .satisfies(exception -> {
                    ItineraryException itineraryException = (ItineraryException) exception;
                    assertThat(itineraryException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(itineraryException.code()).isEqualTo(ItineraryErrorCode.GENERATION_CANDIDATES_NOT_FOUND.code());
                });
    }

    @Test
    void collectsMultipleStructureErrorsInStableInputOrder() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                new AiItineraryDraft("999", List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "9am", 0)),
                        new ItineraryDraftDay(1, Arrays.asList(
                                new ItineraryDraftItem(0, "   ", "09:00", 60),
                                null
                        ))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.GENERATION_ID_MISMATCH,
                        ValidationIssueCode.INVALID_START_TIME,
                        ValidationIssueCode.INVALID_DURATION,
                        ValidationIssueCode.DUPLICATE_DAY,
                        ValidationIssueCode.INVALID_SEQUENCE,
                        ValidationIssueCode.PLACE_ID_REQUIRED,
                        ValidationIssueCode.ITEM_REQUIRED
                );
        assertThat(report.errors())
                .extracting(ValidationIssue::path)
                .containsExactly(
                        "generationId",
                        "days[0].items[0].startTime",
                        "days[0].items[0].durationMinutes",
                        "days[1].day",
                        "days[1].items[0].sequence",
                        "days[1].items[0].placeId",
                        "days[1].items[1]"
                );
    }

    @Test
    void detectsDayCountAndDayRangeWhenTripDayCountIsAvailable() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(3, item(1, "place-1"))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.DAY_COUNT_MISMATCH,
                        ValidationIssueCode.DAY_OUT_OF_RANGE
                );
    }

    @Test
    void skipsCandidateValidationWhenStructureHasErrors() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "required", true)),
                new AiItineraryDraft("10", List.of(day(1, item(1, "outside"))))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.DAY_COUNT_MISMATCH);
    }

    @Test
    void collectsCandidateAndTimeErrorsTogetherWhenStructureIsValid() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", false)),
                new AiItineraryDraft("10", List.of(
                        day(1, List.of(
                                item(1, "place-1", "09:00", 120),
                                item(2, "outside", "10:00", 60)
                        )),
                        day(2, item(1, "place-2"))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                        ValidationIssueCode.ITEM_TIME_OVERLAP
                );
        assertThat(report.errors())
                .extracting(ValidationIssue::path)
                .containsExactly(
                        "days[0].items[1].placeId",
                        "days[0].items[1].startTime"
                );
        assertThat(report.errors().get(1).relatedTargets())
                .extracting(target -> target.sequence())
                .containsExactly(1);
    }

    @Test
    void collectsRequiredPlaceAndDailyWindowErrorsTogetherForV2() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", true)),
                new AiItineraryDraft("10", List.of(
                        day(1, item(1, "place-1", "07:00", 60)),
                        day(2, item(1, "place-1", "09:00", 60))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.REQUIRED_PLACE_MISSING,
                        ValidationIssueCode.OUTSIDE_DAILY_WINDOW
                );
        assertThat(report.errors())
                .extracting(ValidationIssue::path)
                .containsExactly("days", "days[0].items[0].startTime");
    }

    @Test
    void appliesOverlapAndBoundaryToV1ButDoesNotApplyDailyWindow() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshotWithWindow(null, null),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(1, List.of(
                                item(1, "place-1", "07:00", 60),
                                item(2, "place-2", "07:30", 60)
                        )),
                        day(2, item(1, "place-3", "23:30", 90))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.ITEM_TIME_OVERLAP,
                        ValidationIssueCode.ITEM_CROSSES_DAY_BOUNDARY
                );
    }

    @Test
    void rejectsNonStrictStartTimeBeforeTimeRules() {
        AiItineraryValidationReport report = validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "24:00", 60)),
                        day(2, new ItineraryDraftItem(1, "place-2", "9:00", 60))
                ))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.INVALID_START_TIME,
                        ValidationIssueCode.INVALID_START_TIME
                );
    }

    @Test
    void rejectsInvalidDailyWindowForV2AsInvariant() {
        assertThatThrownBy(() -> validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V2,
                inputSnapshotWithWindow(LocalTime.of(8, 0), LocalTime.of(8, 0)),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", false)),
                draft("place-1", "place-2")
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("The itinerary generation contains an invalid daily time window.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.GENERATION_TIME_WINDOW_INVALID.code()));
    }

    @Test
    void validatesReplayStructureWithoutCandidateRules() {
        AiItineraryValidationReport report = validationService.validateStructure(
                10L,
                new AiItineraryDraft("10", List.of(
                        day(1, item(1, "outside-candidate"))
                ))
        );

        assertThat(report.errors()).isEmpty();
    }

    @Test
    void collectsReplayStructureErrors() {
        AiItineraryValidationReport report = validationService.validateStructure(
                10L,
                new AiItineraryDraft("999", Collections.singletonList(null))
        );

        assertThat(report.errors())
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.GENERATION_ID_MISMATCH,
                        ValidationIssueCode.DAY_OUT_OF_RANGE
                );
    }

    @Test
    void rejectsUnsupportedPromptVersionAsInvariantAfterStructurePasses() {
        assertThatThrownBy(() -> validationService.validate(
                10L,
                "itinerary-plan-v999",
                inputSnapshot(List.of()),
                List.of(),
                draft("place-1", "place-2")
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary prompt version is not supported.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.UNSUPPORTED_PROMPT_VERSION.code()));
    }

    @Test
    void warningAndUnverifiedOnlyReportCanPersist() {
        AiItineraryValidationReport report = new AiItineraryValidationReport(
                List.of(),
                List.of(com.planmate.itinerary.api.validation.ValidationIssue.of(
                        ValidationIssueCode.REQUIRED_PLACE_MISSING,
                        "days",
                        null,
                        null,
                        "warning-place"
                )),
                List.of(com.planmate.itinerary.api.validation.ValidationIssue.of(
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                        "days",
                        null,
                        null,
                        "unverified-place"
                ))
        );

        assertThat(report.hasErrors()).isFalse();
        assertThat(report.canPersist()).isTrue();
    }

    @Test
    void validReportDoesNotThrow() {
        assertThatCode(() -> validationService.validate(
                10L,
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                draft("place-1", "place-2")
        )).doesNotThrowAnyException();
    }

    private AiItineraryDraft draft(String day1PlaceId, String day2PlaceId) {
        return new AiItineraryDraft("10", List.of(
                day(1, item(1, day1PlaceId)),
                day(2, item(1, day2PlaceId))
        ));
    }

    private ItineraryDraftDay day(int day, ItineraryDraftItem item) {
        return new ItineraryDraftDay(day, List.of(item));
    }

    private ItineraryDraftDay day(int day, List<ItineraryDraftItem> items) {
        return new ItineraryDraftDay(day, items);
    }

    private ItineraryDraftItem item(int sequence, String placeId) {
        return new ItineraryDraftItem(sequence, placeId, "09:00", 60);
    }

    private ItineraryDraftItem item(int sequence, String placeId, String startTime, int durationMinutes) {
        return new ItineraryDraftItem(sequence, placeId, startTime, durationMinutes);
    }

    private GenerationInputSnapshot inputSnapshot(List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces) {
        return inputSnapshot(mustVisitPlaces, List.of());
    }

    private GenerationInputSnapshot inputSnapshot(
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces,
            List<String> avoidConditions
    ) {
        return inputSnapshotWithWindow(mustVisitPlaces, avoidConditions, LocalTime.of(8, 0), LocalTime.of(20, 0));
    }

    private GenerationInputSnapshot inputSnapshotWithWindow(LocalTime dailyStartTime, LocalTime dailyEndTime) {
        return inputSnapshotWithWindow(List.of(), dailyStartTime, dailyEndTime);
    }

    private GenerationInputSnapshot inputSnapshotWithWindow(
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime
    ) {
        return inputSnapshotWithWindow(mustVisitPlaces, List.of(), dailyStartTime, dailyEndTime);
    }

    private GenerationInputSnapshot inputSnapshotWithWindow(
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces,
            List<String> avoidConditions,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime
    ) {
        return new GenerationInputSnapshot(
                1L,
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
                new GenerationInputSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        new GenerationInputSnapshot.Viewport(34.8, 134.8, 35.2, 135.2),
                        List.of("locality"),
                        "locality"
                ),
                new GenerationInputSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Transportation("WALK", List.of()),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                dailyStartTime,
                dailyEndTime,
                mustVisitPlaces,
                avoidConditions,
                null
        );
    }

    private GenerationInputSnapshot.MustVisitPlace mustVisitPlace(String placeId) {
        return new GenerationInputSnapshot.MustVisitPlace(
                placeId,
                "Place",
                "Address",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId, boolean forcedMustVisit) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place " + rank,
                "Address " + rank,
                new GenerationCandidateSnapshot.Location(35.0 + rank, 135.0 + rank),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of(forcedMustVisit ? "MUST_VISIT" : "CORE_VISIT"),
                forcedMustVisit,
                100.0,
                42.5
        );
    }
}
