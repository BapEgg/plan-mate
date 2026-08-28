package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiItineraryRepeatedPlaceValidationRuleTest {

    private final AiItineraryRepeatedPlaceValidationRule rule =
            new AiItineraryRepeatedPlaceValidationRule();

    @Test
    void createsOneWarningPerPlaceWithSecondOccurrenceAsPrimary() {
        List<ValidationIssue> issues = rule.validate(List.of(
                item(0, 0, 1, 1, " place-a "),
                item(0, 1, 1, 2, "place-b"),
                item(0, 2, 1, 3, "place-a"),
                item(0, 3, 1, 4, "place-a")
        ));

        assertThat(issues).hasSize(1);
        ValidationIssue issue = issues.getFirst();
        assertThat(issue.code()).isEqualTo(ValidationIssueCode.REPEATED_PLACE);
        assertThat(issue.path()).isEqualTo("days[0].items[2].placeId");
        assertThat(issue.sequence()).isEqualTo(3);
        assertThat(issue.placeId()).isEqualTo("place-a");
        assertThat(issue.relatedTargets())
                .extracting(target -> target.path())
                .containsExactly("days[0].items[0]", "days[0].items[3]");
    }

    @Test
    void detectsOccurrencesAcrossDaysAndKeepsPlaceIdsCaseSensitive() {
        List<ValidationIssue> issues = rule.validate(List.of(
                item(0, 0, 1, 1, "place-a"),
                item(1, 0, 2, 1, "PLACE-A"),
                item(2, 0, 3, 1, "place-a")
        ));

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().day()).isEqualTo(3);
    }

    private ValidatedDraftItem item(int dayIndex, int itemIndex, int day, int sequence, String placeId) {
        String normalizedPlaceId = placeId.trim();
        return new ValidatedDraftItem(
                "days[" + dayIndex + "].items[" + itemIndex + "]",
                dayIndex,
                itemIndex,
                day,
                sequence,
                normalizedPlaceId,
                LocalTime.of(9, 0),
                540,
                600,
                60
        );
    }
}
