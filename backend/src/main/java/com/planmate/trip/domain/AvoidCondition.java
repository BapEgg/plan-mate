package com.planmate.trip.domain;

public enum AvoidCondition {
    /**
     * 이른 아침부터 시작하는 일정을 피한다.
     */
    EARLY_MORNING,

    /**
     * 늦은 밤까지 이어지는 일정을 피한다.
     */
    LATE_NIGHT,

    /**
     * 도보 이동 거리가 긴 일정을 피한다.
     */
    LONG_WALK,

    /**
     * 환승이 많은 이동 경로를 피한다.
     */
    MANY_TRANSFERS,

    /**
     * 사람이 많이 몰리는 장소를 피한다.
     */
    CROWDED_PLACE,

    /**
     * 쇼핑 중심 일정을 피한다.
     */
    SHOPPING,

    /**
     * 박물관이나 전시 관람 중심 일정을 피한다.
     */
    MUSEUM,

    /**
     * 비싼 식당 위주의 일정을 피한다.
     */
    EXPENSIVE_RESTAURANT,

    /**
     * 여유 없이 촘촘하게 배치된 일정을 피한다.
     */
    TIGHT_SCHEDULE
}
