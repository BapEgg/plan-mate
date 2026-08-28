package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.api.validation.ValidationTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryRepeatedPlaceValidationRule {

    private static final Comparator<ValidatedDraftItem> OCCURRENCE_ORDER = Comparator
            .comparingInt(ValidatedDraftItem::day)
            .thenComparingInt(ValidatedDraftItem::sequence)
            .thenComparingInt(ValidatedDraftItem::itemIndex);

    public List<ValidationIssue> validate(List<ValidatedDraftItem> items) {
        Map<String, List<ValidatedDraftItem>> occurrencesByPlaceId = new LinkedHashMap<>();
        (items == null ? List.<ValidatedDraftItem>of() : items).stream()
                .sorted(OCCURRENCE_ORDER)
                .forEach(item -> occurrencesByPlaceId
                        .computeIfAbsent(item.placeId(), ignored -> new ArrayList<>())
                        .add(item));

        List<ValidationIssue> issues = new ArrayList<>();
        for (List<ValidatedDraftItem> occurrences : occurrencesByPlaceId.values()) {
            if (occurrences.size() < 2) {
                continue;
            }
            ValidatedDraftItem primary = occurrences.get(1);
            List<ValidationTarget> relatedTargets = new ArrayList<>();
            relatedTargets.add(target(occurrences.get(0)));
            occurrences.stream().skip(2).map(this::target).forEach(relatedTargets::add);
            issues.add(new ValidationIssue(
                    ValidationIssueCode.REPEATED_PLACE,
                    ValidationIssueCode.REPEATED_PLACE.defaultMessage(),
                    primary.path() + ".placeId",
                    primary.day(),
                    primary.sequence(),
                    primary.placeId(),
                    relatedTargets
            ));
        }
        return List.copyOf(issues);
    }

    private ValidationTarget target(ValidatedDraftItem item) {
        return new ValidationTarget(item.path(), item.day(), item.sequence(), item.placeId());
    }
}
