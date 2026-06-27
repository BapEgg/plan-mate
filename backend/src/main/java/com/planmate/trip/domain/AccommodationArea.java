package com.planmate.trip.domain;

public enum AccommodationArea {
    /**
     * 주요 관광지와 방문지가 모여 있는 중심 지역을 선호한다.
     */
    TOURIST_CENTER,

    /**
     * 역, 터미널 등 대중교통 접근성이 좋은 지역을 선호한다.
     */
    TRANSIT,

    /**
     * 번화가보다 조용하고 휴식하기 좋은 지역을 선호한다.
     */
    QUIET,

    /**
     * 숙소 지역에 특별한 선호가 없다.
     */
    ANYWHERE
}
