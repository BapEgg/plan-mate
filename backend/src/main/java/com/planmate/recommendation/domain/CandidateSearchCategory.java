package com.planmate.recommendation.domain;

public enum CandidateSearchCategory {
    /**
     * 사용자가 직접 선택한 필수 방문 장소 후보군이다.
     */
    MUST_VISIT,

    /**
     * 모든 여행에서 기본으로 찾는 대표 관광지 후보군이다.
     */
    CORE_VISIT,

    /**
     * 모든 여행에서 기본으로 찾는 식사 장소 후보군이다.
     */
    MEAL,

    /**
     * 카페와 디저트 장소 후보군이다.
     */
    CAFE,

    /**
     * 역사, 전통, 문화 체험 장소 후보군이다.
     */
    CULTURE,

    /**
     * 자연 경관과 야외 명소 후보군이다.
     */
    NATURE,

    /**
     * 쇼핑 장소와 상권 후보군이다.
     */
    SHOPPING,

    /**
     * 사진 촬영에 적합한 장소 후보군이다.
     */
    PHOTO,

    /**
     * 야경이나 저녁 시간대 방문하기 좋은 장소 후보군이다.
     */
    NIGHT_VIEW,

    /**
     * 체험형 활동과 액티비티 장소 후보군이다.
     */
    ACTIVITY,

    /**
     * 휴식, 산책, 여유로운 장소 후보군이다.
     */
    REST,

    /**
     * 미술관, 전시, 예술 공간 후보군이다.
     */
    ART,

    /**
     * 테마파크와 놀이시설 후보군이다.
     */
    THEME_PARK,

    /**
     * 현지 분위기를 느낄 수 있는 로컬 장소 후보군이다.
     */
    LOCAL
}
