package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.api.validation.ValidationTarget;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryTimeValidationRule {

    private static final long MINUTES_PER_DAY = 24L * 60L;
    private static final Comparator<ValidatedDraftItem> ITEM_LOCAL_ORDER = Comparator
            .comparingInt(ValidatedDraftItem::day)
            .thenComparingInt(ValidatedDraftItem::sequence)
            .thenComparingInt(ValidatedDraftItem::itemIndex);
    private static final Comparator<ValidatedDraftItem> OVERLAP_ORDER = Comparator
            .comparingLong(ValidatedDraftItem::startMinute)
            .thenComparingInt(ValidatedDraftItem::sequence)
            .thenComparingInt(ValidatedDraftItem::itemIndex);

    public List<ValidationIssue> validate(
            String promptVersion,
            GenerationInputSnapshot inputSnapshot,
            List<ValidatedDraftItem> items
    ) {
        List<ValidatedDraftItem> safeItems = items == null ? List.of() : List.copyOf(items);
        boolean validateDailyWindow = ItineraryPromptService.VERSION_V2.equals(promptVersion);
        DailyWindow dailyWindow = validateDailyWindow ? dailyWindow(inputSnapshot) : null;

        List<ValidationIssue> issues = new ArrayList<>();
        Map<Integer, List<ValidatedDraftItem>> itemsByDay = groupByDay(safeItems);
        for (List<ValidatedDraftItem> dayItems : itemsByDay.values()) {
            issues.addAll(validateItemLocalRules(dayItems, dailyWindow));
            issues.addAll(validateOverlaps(dayItems));
        }
        return List.copyOf(issues);
    }

    private DailyWindow dailyWindow(GenerationInputSnapshot inputSnapshot) {
        LocalTime start = inputSnapshot.dailyStartTime();
        LocalTime end = inputSnapshot.dailyEndTime();
        if (start == null || end == null || !start.isBefore(end)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_TIME_WINDOW_INVALID);
        }
        return new DailyWindow(
                AiItineraryTimeParser.minuteOfDay(start),
                AiItineraryTimeParser.minuteOfDay(end)
        );
    }

    private Map<Integer, List<ValidatedDraftItem>> groupByDay(List<ValidatedDraftItem> items) {
        Map<Integer, List<ValidatedDraftItem>> itemsByDay = new LinkedHashMap<>();
        items.stream()
                .sorted(ITEM_LOCAL_ORDER)
                .forEach(item -> itemsByDay
                        .computeIfAbsent(item.day(), ignored -> new ArrayList<>())
                        .add(item));
        return itemsByDay;
    }

    private List<ValidationIssue> validateItemLocalRules(
            List<ValidatedDraftItem> dayItems,
            DailyWindow dailyWindow
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (ValidatedDraftItem item : dayItems) {
            if (item.endMinute() > MINUTES_PER_DAY) {
                issues.add(dayBoundaryIssue(item));
                continue;
            }
            if (dailyWindow != null && isOutsideDailyWindow(item, dailyWindow)) {
                issues.add(dailyWindowIssue(item, dailyWindow));
            }
        }
        return issues;
    }

    private boolean isOutsideDailyWindow(ValidatedDraftItem item, DailyWindow dailyWindow) {
        return item.startMinute() < dailyWindow.startMinute()
                || item.endMinute() > dailyWindow.endMinute();
    }

    private List<ValidationIssue> validateOverlaps(List<ValidatedDraftItem> dayItems) {
        List<ValidatedDraftItem> sortedItems = dayItems.stream()
                .sorted(OVERLAP_ORDER)
                .toList();
        List<ValidationIssue> issues = new ArrayList<>();
        for (int i = 0; i < sortedItems.size(); i++) {
            ValidatedDraftItem related = sortedItems.get(i);
            for (int j = i + 1; j < sortedItems.size(); j++) {
                ValidatedDraftItem primary = sortedItems.get(j);
                if (overlaps(related, primary)) {
                    issues.add(overlapIssue(primary, related));
                }
            }
        }
        return issues;
    }

    private boolean overlaps(ValidatedDraftItem first, ValidatedDraftItem second) {
        return first.startMinute() < second.endMinute()
                && second.startMinute() < first.endMinute();
    }

    private ValidationIssue dayBoundaryIssue(ValidatedDraftItem item) {
        return new ValidationIssue(
                ValidationIssueCode.ITEM_CROSSES_DAY_BOUNDARY,
                "Item interval " + interval(item) + " crosses the day boundary.",
                item.path() + ".durationMinutes",
                item.day(),
                item.sequence(),
                item.placeId(),
                List.of()
        );
    }

    private ValidationIssue dailyWindowIssue(ValidatedDraftItem item, DailyWindow dailyWindow) {
        boolean startsBeforeWindow = item.startMinute() < dailyWindow.startMinute();
        boolean endsAfterWindow = item.endMinute() > dailyWindow.endMinute();
        String path = startsBeforeWindow && endsAfterWindow
                ? item.path()
                : startsBeforeWindow
                        ? item.path() + ".startTime"
                        : item.path() + ".durationMinutes";
        return new ValidationIssue(
                ValidationIssueCode.OUTSIDE_DAILY_WINDOW,
                "Item interval " + interval(item)
                        + " is outside the daily window "
                        + formatMinute(dailyWindow.startMinute())
                        + "-"
                        + formatMinute(dailyWindow.endMinute())
                        + ".",
                path,
                item.day(),
                item.sequence(),
                item.placeId(),
                List.of()
        );
    }

    private ValidationIssue overlapIssue(ValidatedDraftItem primary, ValidatedDraftItem related) {
        return new ValidationIssue(
                ValidationIssueCode.ITEM_TIME_OVERLAP,
                "Item interval " + interval(primary)
                        + " overlaps sequence "
                        + related.sequence()
                        + " interval "
                        + interval(related)
                        + ".",
                primary.path() + ".startTime",
                primary.day(),
                primary.sequence(),
                primary.placeId(),
                List.of(new ValidationTarget(
                        related.path(),
                        related.day(),
                        related.sequence(),
                        related.placeId()
                ))
        );
    }

    private String interval(ValidatedDraftItem item) {
        return formatMinute(item.startMinute()) + "-" + formatMinute(item.endMinute());
    }

    private String formatMinute(long minuteOfDay) {
        long hours = minuteOfDay / 60L;
        long minutes = minuteOfDay % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private record DailyWindow(
            long startMinute,
            long endMinute
    ) {
    }
}
