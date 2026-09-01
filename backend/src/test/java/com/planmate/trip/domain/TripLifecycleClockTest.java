package com.planmate.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripLifecycleClockTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 24);

    @Test
    void isUpcomingBeforeStartInDestinationZone() {
        // 2026-09-20T10:00:00Z == 2026-09-20T19:00:00+09:00 — still the day before START in KST.
        Instant nowUtc = Instant.parse("2026-09-20T10:00:00Z");

        assertThat(TripLifecycleClock.resolve(nowUtc, "Asia/Seoul", START, END))
                .isEqualTo(TripLifecycleClock.TripLifecycleState.UPCOMING);
    }

    @Test
    void becomesOngoingAtLocalMidnightOfStartDateEvenWhenUtcDateIsStillThePreviousDay() {
        // 2026-09-20T15:00:00Z == 2026-09-21T00:00:00+09:00 (KST 자정)
        Instant nowUtc = Instant.parse("2026-09-20T15:00:00Z");

        assertThat(TripLifecycleClock.resolve(nowUtc, "Asia/Seoul", START, END))
                .isEqualTo(TripLifecycleClock.TripLifecycleState.ONGOING);
    }

    @Test
    void stillOngoingUntilEndOfLastDayInDestinationZone() {
        // 2026-09-24T14:59:59Z == 2026-09-24T23:59:59+09:00
        Instant nowUtc = Instant.parse("2026-09-24T14:59:59Z");

        assertThat(TripLifecycleClock.resolve(nowUtc, "Asia/Seoul", START, END))
                .isEqualTo(TripLifecycleClock.TripLifecycleState.ONGOING);
    }

    @Test
    void completedTheDayAfterEndDateInDestinationZone() {
        // 2026-09-24T15:00:00Z == 2026-09-25T00:00:00+09:00
        Instant nowUtc = Instant.parse("2026-09-24T15:00:00Z");

        assertThat(TripLifecycleClock.resolve(nowUtc, "Asia/Seoul", START, END))
                .isEqualTo(TripLifecycleClock.TripLifecycleState.COMPLETED);
    }

    @Test
    void jvmDefaultTimezoneNeverInfluencesTheResult() {
        // 2026-09-24T15:00:00Z is COMPLETED in Asia/Seoul (see completedTheDayAfterEndDateInDestinationZone),
        // but still 2026-09-24 (ONGOING) in America/Los_Angeles. If the implementation ever fell back to
        // the JVM default zone or Instant-truncation instead of the trip's own zone, flipping the JVM
        // default here would silently change the result.
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            Instant nowUtc = Instant.parse("2026-09-24T15:00:00Z");

            assertThat(TripLifecycleClock.resolve(nowUtc, "Asia/Seoul", START, END))
                    .isEqualTo(TripLifecycleClock.TripLifecycleState.COMPLETED);
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }
}
