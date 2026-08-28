package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiItineraryAvoidConditionValidationRuleTest {

    private final AiItineraryAvoidConditionValidationRule rule =
            new AiItineraryAvoidConditionValidationRule();

    @Test
    void accumulatesShoppingAndMuseumViolationsFromTypesAndSourceCategories() {
        AiItineraryAvoidConditionValidationRule.Result result = rule.validate(
                input(List.of("SHOPPING", "MUSEUM")),
                List.of(
                        candidate("shopping-primary", "shopping_mall", List.of(), List.of()),
                        candidate("shopping-type", "tourist_attraction", List.of("gift_shop"), List.of()),
                        candidate("shopping-source", "tourist_attraction", List.of(), List.of("SHOPPING")),
                        candidate("museum-primary", "museum", List.of(), List.of()),
                        candidate("museum-type", "tourist_attraction", List.of("art_gallery"), List.of()),
                        candidate("museum-source", "tourist_attraction", List.of(), List.of("ART"))
                ),
                List.of(
                        item(0, "shopping-primary"), item(1, "shopping-type"),
                        item(2, "shopping-source"), item(3, "museum-primary"),
                        item(4, "museum-type"), item(5, "museum-source")
                )
        );

        assertThat(result.errors()).hasSize(6);
        assertThat(result.errors()).extracting(ValidationIssue::code)
                .containsOnly(ValidationIssueCode.AVOID_CONDITION_VIOLATED);
        assertThat(result.errors()).extracting(ValidationIssue::condition)
                .containsExactly("SHOPPING", "SHOPPING", "SHOPPING", "MUSEUM", "MUSEUM", "MUSEUM");
        assertThat(result.unverifiedConditions()).isEmpty();
    }

    @Test
    void createsOneUnverifiedIssuePerDistinctUnsupportedOrLegacyCondition() {
        List<String> conditions = List.of(
                "EARLY_MORNING", "LATE_NIGHT", "LONG_WALK", "MANY_TRANSFERS",
                "CROWDED_PLACE", "EXPENSIVE_RESTAURANT", "TIGHT_SCHEDULE",
                "LEGACY_CONDITION", " LONG_WALK "
        );

        AiItineraryAvoidConditionValidationRule.Result result = rule.validate(
                input(conditions),
                List.of(candidate("place-a", "tourist_attraction", List.of(), List.of())),
                List.of(item(0, "place-a"))
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.unverifiedConditions()).hasSize(8);
        assertThat(result.unverifiedConditions()).extracting(ValidationIssue::condition)
                .containsExactly(
                        "EARLY_MORNING", "LATE_NIGHT", "LONG_WALK", "MANY_TRANSFERS",
                        "CROWDED_PLACE", "EXPENSIVE_RESTAURANT", "TIGHT_SCHEDULE", "LEGACY_CONDITION"
                );
        assertThat(result.unverifiedConditions()).allSatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(ValidationIssueCode.AVOID_CONDITION_NOT_VERIFIED);
            assertThat(issue.path()).isEqualTo("avoidConditions");
            assertThat(issue.day()).isNull();
            assertThat(issue.sequence()).isNull();
            assertThat(issue.placeId()).isNull();
        });
    }

    @Test
    void skipsItemsOutsideCandidateWhitelist() {
        AiItineraryAvoidConditionValidationRule.Result result = rule.validate(
                input(List.of("SHOPPING")),
                List.of(candidate("allowed", "shopping_mall", List.of(), List.of())),
                List.of(item(0, "outside"))
        );

        assertThat(result.errors()).isEmpty();
    }

    private ValidatedDraftItem item(int itemIndex, String placeId) {
        return new ValidatedDraftItem(
                "days[0].items[" + itemIndex + "]", 0, itemIndex, 1, itemIndex + 1,
                placeId, LocalTime.of(9 + itemIndex, 0), 540L + itemIndex * 60L,
                600L + itemIndex * 60L, 60
        );
    }

    private GenerationCandidateSnapshot candidate(
            String placeId,
            String primaryType,
            List<String> types,
            List<String> sourceCategories
    ) {
        return new GenerationCandidateSnapshot(
                1, placeId, "Place", "Address",
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                primaryType, types, "OPERATIONAL", 4.5, 100,
                List.of(), sourceCategories, false, 100.0, 42.5
        );
    }

    private GenerationInputSnapshot input(List<String> avoidConditions) {
        return new GenerationInputSnapshot(
                1L, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9),
                new GenerationInputSnapshot.Destination(
                        "kyoto", "Kyoto", "Kyoto", 35.0, 135.0,
                        null, List.of("locality"), "locality"
                ),
                new GenerationInputSnapshot.Companion(1, "SOLO", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 100_000L, "BALANCED", List.of()),
                new GenerationInputSnapshot.Preference("BALANCED", List.of()),
                new GenerationInputSnapshot.Transportation("WALK", List.of()),
                new GenerationInputSnapshot.Accommodation(
                        "UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null
                ),
                LocalTime.of(8, 0), LocalTime.of(20, 0), List.of(), avoidConditions, null
        );
    }
}
