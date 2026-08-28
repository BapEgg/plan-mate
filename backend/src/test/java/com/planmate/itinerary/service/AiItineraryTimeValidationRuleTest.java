package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiItineraryTimeValidationRuleTest {

    private final AiItineraryTimeValidationRule rule = new AiItineraryTimeValidationRule();

    @Test
    void detectsItemCrossingDayBoundaryAndAllowsExactMidnightEnd() {
        List<ValidationIssue> crossingIssues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "23:30", 90))
        );
        List<ValidationIssue> midnightEndIssues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "23:00", 60))
        );

        assertThat(crossingIssues)
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.ITEM_CROSSES_DAY_BOUNDARY);
        assertThat(crossingIssues.getFirst().path()).isEqualTo("days[0].items[0].durationMinutes");
        assertThat(crossingIssues.getFirst().day()).isEqualTo(1);
        assertThat(crossingIssues.getFirst().sequence()).isEqualTo(1);
        assertThat(crossingIssues.getFirst().placeId()).isEqualTo("place-1");
        assertThat(midnightEndIssues).isEmpty();
    }

    @Test
    void handlesVeryLargeDurationWithoutIntegerOverflow() {
        List<ValidationIssue> issues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "00:00", Integer.MAX_VALUE))
        );

        assertThat(issues)
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.ITEM_CROSSES_DAY_BOUNDARY);
    }

    @Test
    void validatesDailyWindowOnlyForV2AndSkipsWindowWhenBoundaryFails() {
        List<ValidationIssue> v1Issues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(null, null),
                List.of(item(0, 0, 1, 1, "place-1", "07:59", 30))
        );
        List<ValidationIssue> v2Issues = rule.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(
                        item(0, 0, 1, 1, "place-1", "07:59", 30),
                        item(0, 1, 1, 2, "place-2", "20:00", 1),
                        item(0, 2, 1, 3, "place-3", "23:30", 90)
                )
        );

        assertThat(v1Issues).isEmpty();
        assertThat(v2Issues)
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.OUTSIDE_DAILY_WINDOW,
                        ValidationIssueCode.OUTSIDE_DAILY_WINDOW,
                        ValidationIssueCode.ITEM_CROSSES_DAY_BOUNDARY
                );
        assertThat(v2Issues)
                .extracting(ValidationIssue::path)
                .containsExactly(
                        "days[0].items[0].startTime",
                        "days[0].items[1].durationMinutes",
                        "days[0].items[2].durationMinutes"
                );
    }

    @Test
    void createsSingleDailyWindowIssueWhenStartAndEndAreBothOutsideWindow() {
        List<ValidationIssue> issues = rule.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "07:30", 780))
        );

        assertThat(issues)
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.OUTSIDE_DAILY_WINDOW);
        assertThat(issues.getFirst().path()).isEqualTo("days[0].items[0]");
    }

    @Test
    void rejectsInvalidV2DailyWindowAsGenerationInvariant() {
        assertThatThrownBy(() -> rule.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(null, LocalTime.of(20, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "09:00", 60))
        )).satisfies(this::assertInvalidWindow);
        assertThatThrownBy(() -> rule.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(LocalTime.of(8, 0), null),
                List.of(item(0, 0, 1, 1, "place-1", "09:00", 60))
        )).satisfies(this::assertInvalidWindow);
        assertThatThrownBy(() -> rule.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(8, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "09:00", 60))
        )).satisfies(this::assertInvalidWindow);
        assertThatThrownBy(() -> rule.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(LocalTime.of(20, 0), LocalTime.of(8, 0)),
                List.of(item(0, 0, 1, 1, "place-1", "09:00", 60))
        )).satisfies(this::assertInvalidWindow);
    }

    @Test
    void detectsAllOverlappingPairsWithinSameDay() {
        List<ValidationIssue> issues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(
                        item(0, 0, 1, 1, "place-a", "09:00", 180),
                        item(0, 1, 1, 2, "place-b", "10:00", 60),
                        item(0, 2, 1, 3, "place-c", "11:30", 60),
                        item(1, 0, 2, 1, "place-d", "10:00", 60)
                )
        );

        assertThat(issues)
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationIssueCode.ITEM_TIME_OVERLAP,
                        ValidationIssueCode.ITEM_TIME_OVERLAP
                );
        assertThat(issues)
                .extracting(ValidationIssue::path)
                .containsExactly(
                        "days[0].items[1].startTime",
                        "days[0].items[2].startTime"
                );
        assertThat(issues)
                .extracting(ValidationIssue::sequence)
                .containsExactly(2, 3);
        assertThat(issues.get(0).relatedTargets())
                .extracting(target -> target.sequence())
                .containsExactly(1);
        assertThat(issues.get(1).relatedTargets())
                .extracting(target -> target.sequence())
                .containsExactly(1);
    }

    @Test
    void allowsTouchingIntervalsAndUsesLaterSequenceAsPrimaryWhenStartsAreSame() {
        List<ValidationIssue> touchingIssues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(
                        item(0, 0, 1, 1, "place-a", "09:00", 60),
                        item(0, 1, 1, 2, "place-b", "10:00", 60)
                )
        );
        List<ValidationIssue> sameStartIssues = rule.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                List.of(
                        item(0, 0, 1, 1, "place-a", "09:00", 60),
                        item(0, 1, 1, 2, "place-b", "09:00", 30)
                )
        );

        assertThat(touchingIssues).isEmpty();
        assertThat(sameStartIssues)
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationIssueCode.ITEM_TIME_OVERLAP);
        assertThat(sameStartIssues.getFirst().sequence()).isEqualTo(2);
        assertThat(sameStartIssues.getFirst().relatedTargets())
                .extracting(target -> target.sequence())
                .containsExactly(1);
    }

    private void assertInvalidWindow(Throwable throwable) {
        assertThat(throwable)
                .isInstanceOf(ItineraryException.class)
                .hasMessage("The itinerary generation contains an invalid daily time window.");
        ItineraryException exception = (ItineraryException) throwable;
        assertThat(exception.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.code()).isEqualTo(ItineraryErrorCode.GENERATION_TIME_WINDOW_INVALID.code());
    }

    private ValidatedDraftItem item(
            int dayIndex,
            int itemIndex,
            int day,
            int sequence,
            String placeId,
            String startTime,
            int durationMinutes
    ) {
        LocalTime parsedStartTime = AiItineraryTimeParser.parse(startTime);
        long startMinute = AiItineraryTimeParser.minuteOfDay(parsedStartTime);
        return new ValidatedDraftItem(
                "days[" + dayIndex + "].items[" + itemIndex + "]",
                dayIndex,
                itemIndex,
                day,
                sequence,
                placeId,
                parsedStartTime,
                startMinute,
                startMinute + durationMinutes,
                durationMinutes
        );
    }

    private GenerationInputSnapshot inputSnapshot(LocalTime dailyStartTime, LocalTime dailyEndTime) {
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
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                dailyStartTime,
                dailyEndTime,
                List.of(),
                List.of(),
                null
        );
    }
}
