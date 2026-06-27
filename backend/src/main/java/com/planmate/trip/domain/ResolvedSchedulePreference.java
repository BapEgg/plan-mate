package com.planmate.trip.domain;

import java.time.LocalTime;

public record ResolvedSchedulePreference(
        LocalTime dailyStartTime,
        LocalTime dailyEndTime
) {
}
