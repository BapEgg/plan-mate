package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiItineraryDraftValidationService {

    private final AiItineraryTimeValidationRule timeValidationRule;
    private final AiItineraryAvoidConditionValidationRule avoidConditionValidationRule;
    private final AiItineraryRepeatedPlaceValidationRule repeatedPlaceValidationRule;
    private final AiItineraryRouteValidationRule routeValidationRule;

    public AiItineraryDraftValidationService(
            AiItineraryTimeValidationRule timeValidationRule,
            AiItineraryAvoidConditionValidationRule avoidConditionValidationRule,
            AiItineraryRepeatedPlaceValidationRule repeatedPlaceValidationRule,
            AiItineraryRouteValidationRule routeValidationRule
    ) {
        this.timeValidationRule = timeValidationRule;
        this.avoidConditionValidationRule = avoidConditionValidationRule;
        this.repeatedPlaceValidationRule = repeatedPlaceValidationRule;
        this.routeValidationRule = routeValidationRule;
    }

    public AiItineraryValidationReport validate(
            Long generationId,
            String promptVersion,
            GenerationInputSnapshot inputSnapshot,
            List<GenerationCandidateSnapshot> candidates,
            AiItineraryDraft draft
    ) {
        StructureValidationResult structure = validateStructure(generationId, inputSnapshot.tripDayCount(), draft);
        if (structure.report().hasErrors()) {
            return structure.report();
        }

        AiItineraryValidationReportBuilder builder = new AiItineraryValidationReportBuilder();
        switch (promptVersion) {
            case ItineraryPromptService.VERSION_V1 -> validateV1(
                    inputSnapshot.mustVisitPlaces(),
                    structure.includedPlaceIds(),
                    builder
            );
            case ItineraryPromptService.VERSION_V2 -> validateV2(
                    candidates,
                    structure.includedPlaceIds(),
                    structure.items(),
                    builder
            );
            default -> throw new ItineraryException(ItineraryErrorCode.UNSUPPORTED_PROMPT_VERSION);
        }
        builder.errors(timeValidationRule.validate(promptVersion, inputSnapshot, structure.items()));
        if (ItineraryPromptService.VERSION_V2.equals(promptVersion)) {
            AiItineraryAvoidConditionValidationRule.Result avoidResult = avoidConditionValidationRule.validate(
                    inputSnapshot,
                    candidates,
                    structure.items()
            );
            builder.errors(avoidResult.errors());
            repeatedPlaceValidationRule.validate(structure.items()).forEach(builder::warning);
            avoidResult.unverifiedConditions().forEach(builder::unverifiedCondition);
            if (!builder.hasErrors()) {
                AiItineraryRouteValidationRule.Result routeResult = routeValidationRule.validate(
                        inputSnapshot,
                        candidates,
                        structure.items()
                );
                builder.errors(routeResult.errors());
                routeResult.unverifiedConditions().forEach(builder::unverifiedCondition);
            }
        } else {
            repeatedPlaceValidationRule.validate(structure.items()).forEach(builder::warning);
        }
        return builder.build();
    }

    public AiItineraryValidationReport validateStructure(Long generationId, AiItineraryDraft draft) {
        return validateStructure(generationId, null, draft).report();
    }

    private StructureValidationResult validateStructure(
            Long generationId,
            Integer tripDayCount,
            AiItineraryDraft draft
    ) {
        AiItineraryValidationReportBuilder builder = new AiItineraryValidationReportBuilder();
        if (draft == null) {
            builder.error(ValidationIssueCode.DRAFT_REQUIRED, null, null, null, null);
            return new StructureValidationResult(builder.build(), List.of(), Set.of());
        }
        if (!generationId.toString().equals(draft.generationId())) {
            builder.error(ValidationIssueCode.GENERATION_ID_MISMATCH, "generationId", null, null, null);
        }
        if (draft.days() == null || draft.days().isEmpty()) {
            builder.error(ValidationIssueCode.DAYS_REQUIRED, "days", null, null, null);
            return new StructureValidationResult(builder.build(), List.of(), Set.of());
        }
        if (tripDayCount != null && draft.days().size() != tripDayCount) {
            builder.error(ValidationIssueCode.DAY_COUNT_MISMATCH, "days", null, null, null);
        }

        Set<Integer> dayNumbers = new HashSet<>();
        Set<String> includedPlaceIds = new HashSet<>();
        List<ValidatedDraftItem> items = new java.util.ArrayList<>();
        for (int dayIndex = 0; dayIndex < draft.days().size(); dayIndex++) {
            validateDay(
                    tripDayCount,
                    draft.days().get(dayIndex),
                    dayIndex,
                    dayNumbers,
                    includedPlaceIds,
                    items,
                    builder
            );
        }
        return new StructureValidationResult(builder.build(), items, includedPlaceIds);
    }

    private void validateDay(
            Integer tripDayCount,
            ItineraryDraftDay day,
            int dayIndex,
            Set<Integer> dayNumbers,
            Set<String> includedPlaceIds,
            List<ValidatedDraftItem> items,
            AiItineraryValidationReportBuilder builder
    ) {
        String dayPath = "days[" + dayIndex + "]";
        if (day == null) {
            builder.error(ValidationIssueCode.DAY_OUT_OF_RANGE, dayPath, null, null, null);
            return;
        }

        boolean dayNumberValid = true;
        if (day.day() < 1 || (tripDayCount != null && day.day() > tripDayCount)) {
            builder.error(ValidationIssueCode.DAY_OUT_OF_RANGE, dayPath + ".day", day.day(), null, null);
            dayNumberValid = false;
        }
        if (day.day() >= 1 && !dayNumbers.add(day.day())) {
            builder.error(ValidationIssueCode.DUPLICATE_DAY, dayPath + ".day", day.day(), null, null);
            dayNumberValid = false;
        }
        if (day.items() == null || day.items().isEmpty()) {
            builder.error(ValidationIssueCode.DAY_ITEMS_REQUIRED, dayPath + ".items", dayNumberValid ? day.day() : null, null, null);
            return;
        }

        Set<Integer> sequences = new HashSet<>();
        for (int itemIndex = 0; itemIndex < day.items().size(); itemIndex++) {
            validateItem(
                    day.items().get(itemIndex),
                    dayIndex,
                    itemIndex,
                    day.day(),
                    sequences,
                    includedPlaceIds,
                    items,
                    builder
            );
        }
    }

    private void validateItem(
            ItineraryDraftItem item,
            int dayIndex,
            int itemIndex,
            int day,
            Set<Integer> sequences,
            Set<String> includedPlaceIds,
            List<ValidatedDraftItem> items,
            AiItineraryValidationReportBuilder builder
    ) {
        String itemPath = "days[" + dayIndex + "].items[" + itemIndex + "]";
        if (item == null) {
            builder.error(ValidationIssueCode.ITEM_REQUIRED, itemPath, day, null, null);
            return;
        }

        boolean sequenceValid = true;
        if (item.sequence() < 1) {
            builder.error(ValidationIssueCode.INVALID_SEQUENCE, itemPath + ".sequence", day, item.sequence(), null);
            sequenceValid = false;
        } else if (!sequences.add(item.sequence())) {
            builder.error(ValidationIssueCode.DUPLICATE_SEQUENCE, itemPath + ".sequence", day, item.sequence(), null);
            sequenceValid = false;
        }

        String placeId = normalizePlaceId(item.placeId());
        if (!StringUtils.hasText(placeId)) {
            builder.error(ValidationIssueCode.PLACE_ID_REQUIRED, itemPath + ".placeId", day, sequenceValid ? item.sequence() : null, null);
        } else {
            includedPlaceIds.add(placeId);
        }
        LocalTime startTime = parseTime(item.startTime());
        if (startTime == null) {
            builder.error(ValidationIssueCode.INVALID_START_TIME, itemPath + ".startTime", day, sequenceValid ? item.sequence() : null, placeIdOrNull(placeId));
        }
        boolean durationValid = item.durationMinutes() > 0;
        if (!durationValid) {
            builder.error(ValidationIssueCode.INVALID_DURATION, itemPath + ".durationMinutes", day, sequenceValid ? item.sequence() : null, placeIdOrNull(placeId));
        }

        if (sequenceValid && StringUtils.hasText(placeId) && startTime != null && durationValid) {
            long startMinute = AiItineraryTimeParser.minuteOfDay(startTime);
            items.add(new ValidatedDraftItem(
                    itemPath,
                    dayIndex,
                    itemIndex,
                    day,
                    item.sequence(),
                    placeId,
                    startTime,
                    startMinute,
                    startMinute + item.durationMinutes(),
                    item.durationMinutes()
            ));
        }
    }

    private void validateV1(
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces,
            Set<String> includedPlaceIds,
            AiItineraryValidationReportBuilder builder
    ) {
        for (GenerationInputSnapshot.MustVisitPlace mustVisitPlace : mustVisitPlaces) {
            String requiredPlaceId = normalizePlaceId(mustVisitPlace.placeId());
            if (StringUtils.hasText(requiredPlaceId) && !includedPlaceIds.contains(requiredPlaceId)) {
                builder.error(ValidationIssueCode.REQUIRED_PLACE_MISSING, "days", null, null, requiredPlaceId);
            }
        }
    }

    private void validateV2(
            List<GenerationCandidateSnapshot> candidates,
            Set<String> includedPlaceIds,
            List<ValidatedDraftItem> items,
            AiItineraryValidationReportBuilder builder
    ) {
        List<GenerationCandidateSnapshot> safeCandidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        if (safeCandidates.isEmpty()) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_CANDIDATES_NOT_FOUND);
        }

        Set<String> allowedPlaceIds = new HashSet<>();
        List<String> requiredPlaceIds = safeCandidates.stream()
                .sorted(Comparator.comparingInt(GenerationCandidateSnapshot::rank))
                .map(candidate -> {
                    String placeId = normalizePlaceId(candidate.placeId());
                    if (StringUtils.hasText(placeId)) {
                        allowedPlaceIds.add(placeId);
                        if (candidate.forcedMustVisit()) {
                            return placeId;
                        }
                    }
                    return null;
                })
                .filter(StringUtils::hasText)
                .toList();

        for (ValidatedDraftItem item : items) {
            if (StringUtils.hasText(item.placeId()) && !allowedPlaceIds.contains(item.placeId())) {
                builder.error(
                        ValidationIssueCode.CANDIDATE_NOT_ALLOWED,
                        item.path() + ".placeId",
                        item.day(),
                        item.sequence(),
                        item.placeId()
                );
            }
        }
        for (String requiredPlaceId : requiredPlaceIds) {
            if (!includedPlaceIds.contains(requiredPlaceId)) {
                builder.error(ValidationIssueCode.REQUIRED_PLACE_MISSING, "days", null, null, requiredPlaceId);
            }
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return AiItineraryTimeParser.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            return null;
        }
    }

    private String normalizePlaceId(String value) {
        return value == null ? "" : value.trim();
    }

    private String placeIdOrNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record StructureValidationResult(
            AiItineraryValidationReport report,
            List<ValidatedDraftItem> items,
            Set<String> includedPlaceIds
    ) {
    }
}
