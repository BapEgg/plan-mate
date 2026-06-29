package com.planmate.recommendation.entity;

import com.planmate.recommendation.domain.PlaceTypePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "place_type_policies",
        uniqueConstraints = @UniqueConstraint(name = "place_type_policies_type_name_unique", columnNames = "type_name")
)
public class PlaceTypePolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceTypePolicy policy;

    @Column(name = "score_adjustment", nullable = false)
    private double scoreAdjustment;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PlaceTypePolicyEntity() {
    }

    private PlaceTypePolicyEntity(
            String typeName,
            PlaceTypePolicy policy,
            double scoreAdjustment,
            String reason,
            boolean enabled,
            Instant now
    ) {
        this.typeName = typeName;
        this.policy = policy;
        this.scoreAdjustment = scoreAdjustment;
        this.reason = reason;
        this.enabled = enabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PlaceTypePolicyEntity create(
            String typeName,
            PlaceTypePolicy policy,
            double scoreAdjustment,
            String reason,
            boolean enabled,
            Instant now
    ) {
        return new PlaceTypePolicyEntity(typeName, policy, scoreAdjustment, reason, enabled, now);
    }

    public Long getId() {
        return id;
    }

    public String getTypeName() {
        return typeName;
    }

    public PlaceTypePolicy getPolicy() {
        return policy;
    }

    public double getScoreAdjustment() {
        return scoreAdjustment;
    }

    public String getReason() {
        return reason;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
