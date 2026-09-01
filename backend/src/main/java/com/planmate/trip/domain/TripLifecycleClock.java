package com.planmate.trip.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ADR-0005: 여행 lifecycle 판정의 유일한 근거. 서버 UTC instant와 trip의 IANA timezone만
 * 입력으로 받는다 — device timezone·device clock은 절대 이 계산에 들어가지 않는다(spec §4.3).
 * WP-D(chat cutoff), WP-E(vote deadline)가 이 계산을 재사용한다.
 */
public final class TripLifecycleClock {

    private TripLifecycleClock() {
    }

    public static TripLifecycleState resolve(
            Instant nowUtc,
            String ianaZoneId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        ZoneId zone = ZoneId.of(ianaZoneId);
        LocalDate today = ZonedDateTime.ofInstant(nowUtc, zone).toLocalDate();
        if (today.isBefore(startDate)) {
            return TripLifecycleState.UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return TripLifecycleState.COMPLETED;
        }
        return TripLifecycleState.ONGOING;
    }

    public enum TripLifecycleState {
        UPCOMING,
        ONGOING,
        COMPLETED
    }
}
