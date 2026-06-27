package com.planmate.trip.domain;

public enum TransportMode {
    /**
     * 도보 이동을 주로 사용한다.
     */
    WALK,

    /**
     * 지하철, 버스 등 대중교통을 주로 사용한다.
     */
    PUBLIC_TRANSIT,

    /**
     * 렌터카 이동을 주로 사용한다.
     */
    RENTAL_CAR,

    /**
     * 택시 이동을 주로 사용한다.
     */
    TAXI,

    /**
     * 자전거 이동을 사용한다.
     */
    BIKE,

    /**
     * 투어 차량이나 전용 이동 수단을 사용한다.
     */
    TOUR
}
