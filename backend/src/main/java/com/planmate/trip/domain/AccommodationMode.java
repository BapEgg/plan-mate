package com.planmate.trip.domain;

public enum AccommodationMode {
    /**
     * 숙소가 아직 정해지지 않아 목적지 또는 선호 지역을 기준으로 일정을 만든다.
     */
    UNDECIDED,

    /**
     * Google Places Autocomplete 결과에서 숙소로 사용할 장소를 직접 선택한다.
     */
    PLACE_SEARCH
}
