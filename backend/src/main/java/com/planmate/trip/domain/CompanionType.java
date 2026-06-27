package com.planmate.trip.domain;

public enum CompanionType {
    /**
     * 혼자 떠나는 여행이다.
     */
    SOLO,

    /**
     * 연인 또는 배우자와 함께하는 여행이다.
     */
    COUPLE,

    /**
     * 친구들과 함께하는 여행이다.
     */
    FRIENDS,

    /**
     * 가족 단위 여행이다.
     */
    FAMILY,

    /**
     * 부모님과 함께하는 여행이다.
     */
    PARENTS,

    /**
     * 직장 동료와 함께하는 여행이다.
     */
    COWORKERS,

    /**
     * 위 유형에 명확히 속하지 않는 동행 구성이다.
     */
    OTHER
}
