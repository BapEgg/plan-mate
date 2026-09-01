package com.planmate.common.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Modifying
    @Query(value = """
            DELETE FROM outbox_events
            WHERE id IN (
                SELECT id
                FROM outbox_events
                WHERE created_at < :cutoff
                ORDER BY created_at, id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchBefore(
            @Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize
    );

    /**
     * ADR-0004: crash 복구 reconciliation이 사용할 조회. 기본적으로 비활성 상태인
     * {@link OutboxReconciliationScheduler}만 이 메서드를 호출한다.
     */
    long countByDispatchedAtIsNullAndCreatedAtBefore(Instant cutoff);
}
