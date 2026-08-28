package com.planmate.itinerary.api.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiItineraryValidationReportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsNullListsToEmptyLists() {
        AiItineraryValidationReport report = new AiItineraryValidationReport(null, null, null);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).isEmpty();
        assertThat(report.unverifiedConditions()).isEmpty();
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.canPersist()).isTrue();
    }

    @Test
    void defensivelyCopiesLists() {
        List<ValidationIssue> errors = new ArrayList<>();
        errors.add(ValidationIssue.of(ValidationIssueCode.DRAFT_REQUIRED, null, null, null, null));

        AiItineraryValidationReport report = new AiItineraryValidationReport(errors, List.of(), List.of());
        errors.clear();

        assertThat(report.errors()).hasSize(1);
        assertThatThrownBy(() -> report.errors().add(ValidationIssue.of(
                ValidationIssueCode.DAYS_REQUIRED,
                "days",
                null,
                null,
                null
        ))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void issueUsesDefaultMessageAndDefensivelyCopiesRelatedTargets() {
        List<ValidationTarget> relatedTargets = new ArrayList<>();
        relatedTargets.add(new ValidationTarget("days[0].items[0]", 1, 1, "place-1"));

        ValidationIssue issue = new ValidationIssue(
                ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                null,
                "days[0].items[0].placeId",
                1,
                1,
                "place-2",
                relatedTargets
        );
        relatedTargets.clear();

        assertThat(issue.message()).isEqualTo(ValidationIssueCode.CANDIDATE_NOT_ALLOWED.defaultMessage());
        assertThat(issue.relatedTargets()).hasSize(1);
        assertThatThrownBy(() -> issue.relatedTargets().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serializesEmptyReportListsAsArraysAndIssueCodeAsString() throws Exception {
        AiItineraryValidationReport report = new AiItineraryValidationReport(
                List.of(ValidationIssue.of(
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                        "days[0].items[1].placeId",
                        1,
                        2,
                        "invalid-place"
                )),
                List.of(),
                List.of()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(report));

        assertThat(json.get("errors")).hasSize(1);
        assertThat(json.get("errors").get(0).get("code").asText())
                .isEqualTo("CANDIDATE_NOT_ALLOWED");
        assertThat(json.get("warnings").isArray()).isTrue();
        assertThat(json.get("warnings")).isEmpty();
        assertThat(json.get("unverifiedConditions").isArray()).isTrue();
        assertThat(json.get("unverifiedConditions")).isEmpty();
    }

    @Test
    void serializesConditionOnlyForAvoidIssues() throws Exception {
        AiItineraryValidationReport report = new AiItineraryValidationReport(
                List.of(ValidationIssue.forCondition(
                        ValidationIssueCode.AVOID_CONDITION_VIOLATED,
                        "days[0].items[0].placeId",
                        1,
                        1,
                        "mall",
                        "SHOPPING"
                )),
                List.of(ValidationIssue.of(
                        ValidationIssueCode.REPEATED_PLACE,
                        "days[1].items[0].placeId",
                        2,
                        1,
                        "mall"
                )),
                List.of()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(report));

        assertThat(json.get("errors").get(0).get("condition").asText()).isEqualTo("SHOPPING");
        assertThat(json.get("warnings").get(0).has("condition")).isFalse();
    }
}
