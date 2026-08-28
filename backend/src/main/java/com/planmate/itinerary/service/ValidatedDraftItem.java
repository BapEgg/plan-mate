package com.planmate.itinerary.service;

import java.time.LocalTime;

record ValidatedDraftItem(
        String path,
        int dayIndex,
        int itemIndex,
        int day,
        int sequence,
        String placeId,
        LocalTime startTime,
        long startMinute,
        long endMinute,
        int durationMinutes
) {
}
