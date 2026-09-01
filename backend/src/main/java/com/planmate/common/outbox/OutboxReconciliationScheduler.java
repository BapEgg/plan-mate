package com.planmate.common.outbox;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ADR-0004: outbox dispatcher skeleton. 기본 비활성 — realtime fan-out을 outbox에 연결하는
 * 소비자가 아직 없어서 지금 켜면 모든 row를 항상 undispatched로 관측하게 된다(오탐).
 * WP-B/D가 realtime publish 성공 시 {@link OutboxEventEntity#markDispatched}를 호출하는
 * 두 번째 event type을 추가하면, 이 scheduler를 켜서 process crash로 유실된 publish를
 * 관측할 수 있다. 지금은 관측(logging)만 하고 재발행은 하지 않는다 — event_type별 STOMP
 * 목적지 매핑이 아직 없기 때문이다.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.outbox.reconciliation",
        name = "enabled",
        havingValue = "true"
)
public class OutboxReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxReconciliationScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxReconciliationProperties properties;
    private final Clock clock;

    public OutboxReconciliationScheduler(
            OutboxEventRepository outboxEventRepository,
            OutboxReconciliationProperties properties,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.reconciliation.scan-interval:5m}")
    public void reportUndispatched() {
        Instant cutoff = Instant.now(clock).minus(properties.getStaleAfter());
        long undispatchedCount = outboxEventRepository.countByDispatchedAtIsNullAndCreatedAtBefore(cutoff);
        if (undispatchedCount > 0) {
            log.warn("Outbox rows older than {} have no recorded realtime dispatch: count={}", cutoff, undispatchedCount);
        }
    }
}
