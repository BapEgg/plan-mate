package com.planmate.itinerary.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiItineraryValidationMetricsTest {

    private SimpleMeterRegistry registry;
    private AiItineraryValidationMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiItineraryValidationMetrics(registry);
    }

    @Test
    void recordsPassedAttemptWhenReportHasNoErrors() {
        metrics.recordValidate(AiItineraryValidationReport.empty());

        assertAttempt("validate", "passed", 1.0);
    }

    @Test
    void recordsBlockedAttemptAndEveryErrorIssue() {
        ValidationIssue first = issue(ValidationIssueCode.CANDIDATE_NOT_ALLOWED);
        ValidationIssue second = issue(ValidationIssueCode.CANDIDATE_NOT_ALLOWED);
        ValidationIssue third = issue(ValidationIssueCode.OUTSIDE_DAILY_WINDOW);

        metrics.recordSubmit(new AiItineraryValidationReport(
                List.of(first, second, third),
                List.of(),
                List.of()
        ));

        assertAttempt("submit", "blocked", 1.0);
        assertIssue("submit", "error", ValidationIssueCode.CANDIDATE_NOT_ALLOWED, 2.0);
        assertIssue("submit", "error", ValidationIssueCode.OUTSIDE_DAILY_WINDOW, 1.0);
    }

    @Test
    void warningAndUnverifiedReportsRemainPassedAndUseTheirSeverity() {
        metrics.recordValidate(new AiItineraryValidationReport(
                List.of(),
                List.of(issue(ValidationIssueCode.REPEATED_PLACE)),
                List.of()
        ));
        metrics.recordSubmit(new AiItineraryValidationReport(
                List.of(),
                List.of(),
                List.of(issue(ValidationIssueCode.ADJACENT_ROUTE_NOT_VERIFIED))
        ));

        assertAttempt("validate", "passed", 1.0);
        assertAttempt("submit", "passed", 1.0);
        assertIssue("validate", "warning", ValidationIssueCode.REPEATED_PLACE, 1.0);
        assertIssue("submit", "unverified", ValidationIssueCode.ADJACENT_ROUTE_NOT_VERIFIED, 1.0);
    }

    @Test
    void validateAndSubmitSourcesAreRecordedSeparately() {
        metrics.recordValidate(AiItineraryValidationReport.empty());
        metrics.recordSubmit(AiItineraryValidationReport.empty());

        assertAttempt("validate", "passed", 1.0);
        assertAttempt("submit", "passed", 1.0);
    }

    private ValidationIssue issue(ValidationIssueCode code) {
        return ValidationIssue.of(code, "days[0].items[0]", 1, 1, "place-1");
    }

    private void assertAttempt(String source, String result, double count) {
        assertThat(registry.get("planmate.itinerary.validation.attempt")
                .tags("source", source, "result", result)
                .counter()
                .count()).isEqualTo(count);
    }

    private void assertIssue(
            String source,
            String severity,
            ValidationIssueCode code,
            double count
    ) {
        assertThat(registry.get("planmate.itinerary.validation.issue")
                .tags("source", source, "severity", severity, "code", code.name())
                .counter()
                .count()).isEqualTo(count);
    }
}
