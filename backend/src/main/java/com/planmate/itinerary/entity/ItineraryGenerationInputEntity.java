package com.planmate.itinerary.entity;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "itinerary_generation_inputs")
public class ItineraryGenerationInputEntity {

    @Id
    @Column(name = "generation_id", nullable = false)
    private Long generationId;

    @Column(name = "snapshot_version", nullable = false)
    private int snapshotVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private GenerationInputSnapshot payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ItineraryGenerationInputEntity() {
    }

    private ItineraryGenerationInputEntity(
            Long generationId,
            int snapshotVersion,
            GenerationInputSnapshot payload,
            Instant createdAt
    ) {
        this.generationId = Objects.requireNonNull(generationId);
        this.snapshotVersion = snapshotVersion;
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static ItineraryGenerationInputEntity create(
            Long generationId,
            int snapshotVersion,
            GenerationInputSnapshot payload,
            Instant createdAt
    ) {
        return new ItineraryGenerationInputEntity(generationId, snapshotVersion, payload, createdAt);
    }

    public Long getGenerationId() {
        return generationId;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public GenerationInputSnapshot getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
