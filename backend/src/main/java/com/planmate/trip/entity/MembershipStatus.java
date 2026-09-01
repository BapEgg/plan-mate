package com.planmate.trip.entity;

/**
 * ADR-0001: membership interval 상태. 나가기/내보내기는 같은 행을 LEFT/REMOVED로 종료하고,
 * 재가입은 새 행을 ACTIVE로 추가한다 — 과거 interval 행은 다시 열리지 않는다.
 */
public enum MembershipStatus {

    ACTIVE,
    LEFT,
    REMOVED

}
