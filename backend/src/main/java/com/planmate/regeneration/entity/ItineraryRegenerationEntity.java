package com.planmate.regeneration.entity;

import com.planmate.itinerary.dto.AiItineraryDraft;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "itinerary_regenerations")
public class ItineraryRegenerationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "generation_id", nullable = false, unique = true)
    private Long generationId;

    @Column(name = "base_itinerary_id", nullable = false)
    private Long baseItineraryId;

    @Column(name = "base_itinerary_version", nullable = false)
    private int baseItineraryVersion;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegenerationScopeType scope;

    @Column(name = "day_number")
    private Integer dayNumber;

    @Column(name = "start_item_id")
    private Long startItemId;

    @Column(name = "end_item_id")
    private Long endItemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fixed_item_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> fixedItemIds;

    @Column(name = "additional_request", length = 1000)
    private String additionalRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItineraryRegenerationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_payload", columnDefinition = "jsonb")
    private AiItineraryDraft draftPayload;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "applied_itinerary_id")
    private Long appliedItineraryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItineraryRegenerationEntity() {
    }

    private ItineraryRegenerationEntity(
            Long tripId,
            Long generationId,
            Long baseItineraryId,
            int baseItineraryVersion,
            Long requestedByUserId,
            RegenerationScopeType scope,
            Integer dayNumber,
            Long startItemId,
            Long endItemId,
            List<Long> fixedItemIds,
            String additionalRequest,
            Instant now
    ) {
        this.tripId = tripId;
        this.generationId = generationId;
        this.baseItineraryId = baseItineraryId;
        this.baseItineraryVersion = baseItineraryVersion;
        this.requestedByUserId = requestedByUserId;
        this.scope = scope;
        this.dayNumber = dayNumber;
        this.startItemId = startItemId;
        this.endItemId = endItemId;
        this.fixedItemIds = fixedItemIds == null ? List.of() : List.copyOf(fixedItemIds);
        this.additionalRequest = additionalRequest;
        this.status = ItineraryRegenerationStatus.GENERATING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryRegenerationEntity create(
            Long tripId,
            Long generationId,
            Long baseItineraryId,
            int baseItineraryVersion,
            Long requestedByUserId,
            RegenerationScopeType scope,
            Integer dayNumber,
            Long startItemId,
            Long endItemId,
            List<Long> fixedItemIds,
            String additionalRequest,
            Instant now
    ) {
        return new ItineraryRegenerationEntity(
                tripId, generationId, baseItineraryId, baseItineraryVersion, requestedByUserId,
                scope, dayNumber, startItemId, endItemId, fixedItemIds, additionalRequest, now
        );
    }

    public void markReady(AiItineraryDraft draft, Instant now) {
        if (status == ItineraryRegenerationStatus.READY_FOR_REVIEW && draftPayload != null) return;
        requireStatus(ItineraryRegenerationStatus.GENERATING);
        this.draftPayload = draft;
        this.failureReason = null;
        this.status = ItineraryRegenerationStatus.READY_FOR_REVIEW;
        this.updatedAt = now;
    }

    public void markApplied(Long itineraryId, Instant now) {
        requireStatus(ItineraryRegenerationStatus.READY_FOR_REVIEW);
        this.status = ItineraryRegenerationStatus.APPLIED;
        this.appliedItineraryId = itineraryId;
        this.updatedAt = now;
    }

    public void markRejected(Instant now) {
        requireStatus(ItineraryRegenerationStatus.READY_FOR_REVIEW);
        this.status = ItineraryRegenerationStatus.REJECTED;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        if (status != ItineraryRegenerationStatus.GENERATING) return;
        this.status = ItineraryRegenerationStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void markStale(Instant now) {
        if (status == ItineraryRegenerationStatus.APPLIED) return;
        this.status = ItineraryRegenerationStatus.STALE;
        this.updatedAt = now;
    }

    private void requireStatus(ItineraryRegenerationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Cannot transition regeneration from " + status + ", expected " + expected);
        }
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getGenerationId() { return generationId; }
    public Long getBaseItineraryId() { return baseItineraryId; }
    public int getBaseItineraryVersion() { return baseItineraryVersion; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public RegenerationScopeType getScope() { return scope; }
    public Integer getDayNumber() { return dayNumber; }
    public Long getStartItemId() { return startItemId; }
    public Long getEndItemId() { return endItemId; }
    public List<Long> getFixedItemIds() { return fixedItemIds == null ? List.of() : List.copyOf(fixedItemIds); }
    public String getAdditionalRequest() { return additionalRequest; }
    public ItineraryRegenerationStatus getStatus() { return status; }
    public AiItineraryDraft getDraftPayload() { return draftPayload; }
    public String getFailureReason() { return failureReason; }
    public Long getAppliedItineraryId() { return appliedItineraryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
