package com.planmate.itinerary.entity;

public enum ItineraryGenerationStatus {
    /**
     * 일정 생성 요청이 생성되었지만 후보 수집은 아직 시작되지 않은 상태다.
     */
    CREATED,

    /**
     * Google Places 후보지를 수집하고 있는 상태다.
     */
    COLLECTING_CANDIDATES,

    /**
     * 후보 수집과 프롬프트 준비가 끝나 AI 계획 생성을 기다리는 상태다.
     */
    READY_FOR_PLANNING,

    /**
     * AI 또는 수동 입력을 통해 일정 계획을 생성하고 있는 상태다.
     */
    PLANNING,

    /**
     * 생성된 일정 결과를 저장 전에 검증하는 상태다.
     */
    VALIDATING,

    /**
     * 검증된 일정이 저장되어 생성 흐름이 완료된 상태다.
     */
    COMPLETED,

    /**
     * 후보 수집, 프롬프트 준비, 응답 검증, 저장 중 실패한 상태다.
     */
    FAILED
}
