package com.planmate.itinerary.entity;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "itinerary_generations")
public class ItineraryGenerationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ItineraryGenerationStatus status;

    @Column(nullable = false, length = 80)
    private String promptVersion;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "collection_claim_version", nullable = false)
    private long collectionClaimVersion;

    @Column(name = "collection_lease_expires_at")
    private Instant collectionLeaseExpiresAt;

    protected ItineraryGenerationEntity() {
    }

    private ItineraryGenerationEntity(Long tripId, String promptVersion, Instant now) {
        this.tripId = tripId;
        this.status = ItineraryGenerationStatus.CREATED;
        this.promptVersion = promptVersion;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryGenerationEntity create(Long tripId, String promptVersion, Instant now) {
        return new ItineraryGenerationEntity(tripId, promptVersion, now);
    }

    public void markCollecting(Instant now) {
        requireStatus(ItineraryGenerationStatus.CREATED);
        status = ItineraryGenerationStatus.COLLECTING_CANDIDATES;
        failureReason = null;
        updatedAt = now;
    }

    public long claimCollection(Instant now, Duration leaseDuration, boolean redelivered) {
        if (status == ItineraryGenerationStatus.CREATED) {
            markCollecting(now);
        } else if (status != ItineraryGenerationStatus.COLLECTING_CANDIDATES
                || (!redelivered && hasValidCollectionLease(now))) {
            return -1L;
        }
        collectionClaimVersion++;
        collectionLeaseExpiresAt = now.plus(leaseDuration);
        updatedAt = now;
        return collectionClaimVersion;
    }

    public boolean ownsCollectionClaim(long claimVersion) {
        return status == ItineraryGenerationStatus.COLLECTING_CANDIDATES
                && collectionClaimVersion == claimVersion;
    }

    private boolean hasValidCollectionLease(Instant now) {
        return collectionLeaseExpiresAt != null && collectionLeaseExpiresAt.isAfter(now);
    }

    public void markReady(Instant now) {
        requireStatus(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
        status = ItineraryGenerationStatus.READY_FOR_PLANNING;
        failureReason = null;
        updatedAt = now;
    }

    public void markCompleted(Instant now) {
        requireStatus(ItineraryGenerationStatus.READY_FOR_PLANNING);
        status = ItineraryGenerationStatus.COMPLETED;
        failureReason = null;
        updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        if (status != ItineraryGenerationStatus.CREATED
                && status != ItineraryGenerationStatus.COLLECTING_CANDIDATES) {
            throw new IllegalStateException(
                    "Cannot transition itinerary generation from "
                            + status
                            + ", expected CREATED or COLLECTING_CANDIDATES"
            );
        }
        status = ItineraryGenerationStatus.FAILED;
        failureReason = reason;
        updatedAt = now;
    }

    private void requireStatus(ItineraryGenerationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot transition itinerary generation from "
                            + status
                            + ", expected "
                            + expected
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public ItineraryGenerationStatus getStatus() {
        return status;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getCollectionClaimVersion() {
        return collectionClaimVersion;
    }

    public Instant getCollectionLeaseExpiresAt() {
        return collectionLeaseExpiresAt;
    }
}
