package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryAvoidConditionValidationRule {

    private static final String SHOPPING = "SHOPPING";
    private static final String MUSEUM = "MUSEUM";
    private static final Set<String> SHOPPING_TYPES = Set.of(
            "shopping_mall", "department_store", "clothing_store", "shoe_store",
            "jewelry_store", "gift_shop", "market"
    );
    private static final Set<String> MUSEUM_TYPES = Set.of(
            "museum", "art_gallery", "art_museum", "history_museum"
    );

    public Result validate(
            GenerationInputSnapshot inputSnapshot,
            List<GenerationCandidateSnapshot> candidates,
            List<ValidatedDraftItem> items
    ) {
        Set<String> conditions = normalizedConditions(inputSnapshot.avoidConditions());
        Map<String, GenerationCandidateSnapshot> candidatesByPlaceId = candidatesByPlaceId(candidates);
        List<ValidationIssue> errors = new ArrayList<>();

        for (ValidatedDraftItem item : items == null ? List.<ValidatedDraftItem>of() : items) {
            GenerationCandidateSnapshot candidate = candidatesByPlaceId.get(item.placeId());
            if (candidate == null) {
                continue;
            }
            if (conditions.contains(SHOPPING) && matches(candidate, "SHOPPING", SHOPPING_TYPES)) {
                errors.add(violation(item, SHOPPING));
            }
            if (conditions.contains(MUSEUM) && matches(candidate, "ART", MUSEUM_TYPES)) {
                errors.add(violation(item, MUSEUM));
            }
        }

        List<ValidationIssue> unverifiedConditions = conditions.stream()
                .filter(condition -> !SHOPPING.equals(condition) && !MUSEUM.equals(condition))
                .map(this::unverified)
                .toList();
        return new Result(errors, unverifiedConditions);
    }

    private Set<String> normalizedConditions(List<String> conditions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (conditions != null) {
            conditions.stream()
                    .filter(condition -> condition != null && !condition.isBlank())
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        return normalized;
    }

    private Map<String, GenerationCandidateSnapshot> candidatesByPlaceId(
            List<GenerationCandidateSnapshot> candidates
    ) {
        Map<String, GenerationCandidateSnapshot> result = new HashMap<>();
        if (candidates != null) {
            for (GenerationCandidateSnapshot candidate : candidates) {
                if (candidate != null && candidate.placeId() != null && !candidate.placeId().isBlank()) {
                    result.putIfAbsent(candidate.placeId().trim(), candidate);
                }
            }
        }
        return result;
    }

    private boolean matches(
            GenerationCandidateSnapshot candidate,
            String sourceCategory,
            Set<String> placeTypes
    ) {
        return candidate.sourceCategories().contains(sourceCategory)
                || placeTypes.contains(candidate.primaryType())
                || candidate.types().stream().anyMatch(placeTypes::contains);
    }

    private ValidationIssue violation(ValidatedDraftItem item, String condition) {
        return ValidationIssue.forCondition(
                ValidationIssueCode.AVOID_CONDITION_VIOLATED,
                item.path() + ".placeId",
                item.day(),
                item.sequence(),
                item.placeId(),
                condition
        );
    }

    private ValidationIssue unverified(String condition) {
        return ValidationIssue.forCondition(
                ValidationIssueCode.AVOID_CONDITION_NOT_VERIFIED,
                "avoidConditions",
                null,
                null,
                null,
                condition
        );
    }

    public record Result(
            List<ValidationIssue> errors,
            List<ValidationIssue> unverifiedConditions
    ) {
        public Result {
            errors = List.copyOf(errors);
            unverifiedConditions = List.copyOf(unverifiedConditions);
        }
    }
}
