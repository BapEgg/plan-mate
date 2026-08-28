package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryDraftNormalizer {

    public NormalizedAiItineraryDraft normalize(Long generationId, AiItineraryDraft draft) {
        requireValidInternalCall(draft != null, "draft must not be null");
        requireValidInternalCall(draft.days() != null, "days must not be null");

        List<NormalizedAiItineraryDraft.Day> days = draft.days().stream()
                .map(this::normalizeDay)
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Day::day))
                .toList();
        return new NormalizedAiItineraryDraft(generationId, days);
    }

    public NormalizedAiItineraryDraft normalize(ItineraryEntity itinerary) {
        List<NormalizedAiItineraryDraft.Day> days = itinerary.getDays().stream()
                .map(this::normalizeDay)
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Day::day))
                .toList();
        return new NormalizedAiItineraryDraft(itinerary.getGeneration().getId(), days);
    }

    private NormalizedAiItineraryDraft.Day normalizeDay(ItineraryDraftDay day) {
        requireValidInternalCall(day != null, "day must not be null");
        requireValidInternalCall(day.items() != null, "day items must not be null");

        List<NormalizedAiItineraryDraft.Item> items = day.items().stream()
                .map(this::normalizeItem)
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Item::sequence))
                .toList();
        return new NormalizedAiItineraryDraft.Day(day.day(), items);
    }

    private NormalizedAiItineraryDraft.Item normalizeItem(ItineraryDraftItem item) {
        requireValidInternalCall(item != null, "item must not be null");
        String placeId = normalizePlaceId(item.placeId());
        return new NormalizedAiItineraryDraft.Item(
                item.sequence(),
                placeId,
                parseTime(item.startTime()),
                item.durationMinutes()
        );
    }

    private NormalizedAiItineraryDraft.Day normalizeDay(ItineraryDayEntity day) {
        List<NormalizedAiItineraryDraft.Item> items = day.getItems().stream()
                .map(this::normalizeItem)
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Item::sequence))
                .toList();
        return new NormalizedAiItineraryDraft.Day(day.getDay(), items);
    }

    private NormalizedAiItineraryDraft.Item normalizeItem(ItineraryItemEntity item) {
        return new NormalizedAiItineraryDraft.Item(
                item.getSequence(),
                normalizePlaceId(item.getPlaceId()),
                item.getStartTime(),
                item.getDurationMinutes()
        );
    }

    private LocalTime parseTime(String value) {
        try {
            return AiItineraryTimeParser.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("startTime must use HH:mm format.", exception);
        }
    }

    private String normalizePlaceId(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireValidInternalCall(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
