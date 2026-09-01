package com.planmate.trip.api;

/**
 * WP-B: OWNER 전용 command를 트리거하는 도메인(invitation 등)이 {@code trip.entity}를 직접
 * 다루지 않고도 role을 재검사할 수 있게 하는 port. 멤버가 아니면 존재 여부를 노출하지 않기 위해
 * "not found" 계열 예외를, 멤버지만 OWNER가 아니면 403 계열 예외를 던진다.
 */
public interface TripRoleChecker {

    void requireOwner(Long userId, Long tripId);
}
