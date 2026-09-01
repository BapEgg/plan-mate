package com.planmate.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    protected OutboxEventEntity() {
    }

    private OutboxEventEntity(
            String aggregateType,
            String aggregateId,
            String eventType,
            Map<String, Object> payload,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = new LinkedHashMap<>(payload);
        this.createdAt = createdAt;
    }

    public static OutboxEventEntity create(
            String aggregateType,
            String aggregateId,
            String eventType,
            Map<String, Object> payload,
            Instant createdAt
    ) {
        return new OutboxEventEntity(aggregateType, aggregateId, eventType, payload, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return Map.copyOf(payload);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public int getDispatchAttempts() {
        return dispatchAttempts;
    }

    /**
     * ADR-0004: realtime fan-out publish가 성공했음을 기록한다. WP-A는 이 mutator를 제공만
     * 하고 호출자(reconciliation)는 WP-B/D가 두 번째 realtime event type을 추가할 때 배선한다.
     */
    public void markDispatched(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
        this.dispatchAttempts++;
    }
}
