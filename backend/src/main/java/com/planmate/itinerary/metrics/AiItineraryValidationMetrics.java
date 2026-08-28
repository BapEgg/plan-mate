package com.planmate.itinerary.metrics;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryValidationMetrics {

    private static final String ATTEMPT_METRIC = "planmate.itinerary.validation.attempt";
    private static final String ISSUE_METRIC = "planmate.itinerary.validation.issue";
    private static final String SOURCE_VALIDATE = "validate";
    private static final String SOURCE_SUBMIT = "submit";

    private final MeterRegistry meterRegistry;

    public AiItineraryValidationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordValidate(AiItineraryValidationReport report) {
        record(SOURCE_VALIDATE, report);
    }

    public void recordSubmit(AiItineraryValidationReport report) {
        record(SOURCE_SUBMIT, report);
    }

    private void record(String source, AiItineraryValidationReport report) {
        Counter.builder(ATTEMPT_METRIC)
                .description("Manual itinerary validation attempt count.")
                .tag("source", source)
                .tag("result", report.hasErrors() ? "blocked" : "passed")
                .register(meterRegistry)
                .increment();
        recordIssues(source, "error", report.errors());
        recordIssues(source, "warning", report.warnings());
        recordIssues(source, "unverified", report.unverifiedConditions());
    }

    private void recordIssues(String source, String severity, List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            Counter.builder(ISSUE_METRIC)
                    .description("Manual itinerary validation issue count.")
                    .tag("source", source)
                    .tag("severity", severity)
                    .tag("code", issue.code().name())
                    .register(meterRegistry)
                    .increment();
        }
    }
}
