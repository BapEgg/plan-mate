package com.planmate.common.web;

/**
 * C0: 상태를 바꾸는 모든 command endpoint(WP-B 이후)가 공통으로 받는 idempotency key header
 * 이름. 같은 key로 재전송된 요청은 최초 처리 결과를 그대로 반환해야 하며 부작용을 다시
 * 실행하지 않는다(spec §8 "공통 command 규칙"). WP-A는 이름만 고정하고 실제 저장/조회 로직은
 * 첫 command를 구현하는 package(WP-B)가 추가한다.
 */
public final class IdempotencyKey {

    public static final String HEADER_NAME = "Idempotency-Key";

    private IdempotencyKey() {
    }
}
