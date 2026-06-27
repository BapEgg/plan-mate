package com.planmate.recommendation.domain;

public enum SearchAnchorType {
    /**
     * 목적지 Place Details의 좌표 또는 viewport를 후보 검색 기준으로 사용한다.
     */
    DESTINATION,

    /**
     * 선택된 숙소의 좌표를 후보 검색 기준으로 사용한다.
     */
    ACCOMMODATION
}
