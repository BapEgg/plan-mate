package com.planmate.proposal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "itinerary_proposals")
public class ItineraryProposalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "base_itinerary_id", nullable = false)
    private Long baseItineraryId;

    @Column(name = "base_itinerary_version", nullable = false)
    private int baseItineraryVersion;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "proposal_type", nullable = false, length = 32)
    private String proposalType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItineraryProposalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_mode", length = 16)
    private ProposalDecisionMode decisionMode;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "target_item_id", nullable = false)
    private Long targetItemId;

    @Column(name = "replacement_place_id", nullable = false, length = 255)
    private String replacementPlaceId;

    @Column(name = "replacement_display_name", nullable = false, length = 255)
    private String replacementDisplayName;

    @Column(name = "replacement_start_time", nullable = false)
    private LocalTime replacementStartTime;

    @Column(name = "replacement_duration_minutes", nullable = false)
    private int replacementDurationMinutes;

    @Column(name = "canonical_fingerprint", nullable = false, length = 64)
    private String canonicalFingerprint;

    @Column(name = "applied_itinerary_id")
    private Long appliedItineraryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItineraryProposalEntity() {
    }

    private ItineraryProposalEntity(
            Long tripId,
            Long baseItineraryId,
            int baseItineraryVersion,
            Long createdByUserId,
            int dayNumber,
            Long targetItemId,
            String replacementPlaceId,
            String replacementDisplayName,
            LocalTime replacementStartTime,
            int replacementDurationMinutes,
            String canonicalFingerprint,
            Instant now
    ) {
        this.tripId = tripId;
        this.baseItineraryId = baseItineraryId;
        this.baseItineraryVersion = baseItineraryVersion;
        this.createdByUserId = createdByUserId;
        this.proposalType = "REPLACE_ITEM";
        this.status = ItineraryProposalStatus.READY;
        this.dayNumber = dayNumber;
        this.targetItemId = targetItemId;
        this.replacementPlaceId = replacementPlaceId;
        this.replacementDisplayName = replacementDisplayName;
        this.replacementStartTime = replacementStartTime;
        this.replacementDurationMinutes = replacementDurationMinutes;
        this.canonicalFingerprint = canonicalFingerprint;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryProposalEntity replaceItem(
            Long tripId,
            Long baseItineraryId,
            int baseItineraryVersion,
            Long createdByUserId,
            int dayNumber,
            Long targetItemId,
            String replacementPlaceId,
            String replacementDisplayName,
            LocalTime replacementStartTime,
            int replacementDurationMinutes,
            String canonicalFingerprint,
            Instant now
    ) {
        return new ItineraryProposalEntity(
                tripId,
                baseItineraryId,
                baseItineraryVersion,
                createdByUserId,
                dayNumber,
                targetItemId,
                replacementPlaceId,
                replacementDisplayName,
                replacementStartTime,
                replacementDurationMinutes,
                canonicalFingerprint,
                now
        );
    }

    public void openVote(Instant now) {
        requireReady();
        decisionMode = ProposalDecisionMode.VOTE;
        status = ItineraryProposalStatus.VOTE_OPEN;
        updatedAt = now;
    }

    public void selectDirect(Instant now) {
        requireReady();
        decisionMode = ProposalDecisionMode.DIRECT;
        updatedAt = now;
    }

    public void markApplied(Long itineraryId, Instant now) {
        if (status != ItineraryProposalStatus.READY && status != ItineraryProposalStatus.VOTE_OPEN) {
            throw new IllegalStateException("Proposal cannot be applied from " + status);
        }
        status = ItineraryProposalStatus.APPLIED;
        appliedItineraryId = itineraryId;
        updatedAt = now;
    }

    public void markRejected(Instant now) {
        if (status != ItineraryProposalStatus.VOTE_OPEN) {
            throw new IllegalStateException("Proposal cannot be rejected from " + status);
        }
        status = ItineraryProposalStatus.REJECTED;
        updatedAt = now;
    }

    public void markCancelled(Instant now) {
        if (status == ItineraryProposalStatus.APPLIED) {
            throw new IllegalStateException("Applied proposal cannot be cancelled");
        }
        status = ItineraryProposalStatus.CANCELLED;
        updatedAt = now;
    }

    public void markStale(Instant now) {
        if (status == ItineraryProposalStatus.APPLIED) {
            throw new IllegalStateException("Applied proposal cannot become stale");
        }
        status = ItineraryProposalStatus.STALE;
        updatedAt = now;
    }

    private void requireReady() {
        if (status != ItineraryProposalStatus.READY) {
            throw new IllegalStateException("Proposal is not ready: " + status);
        }
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getBaseItineraryId() { return baseItineraryId; }
    public int getBaseItineraryVersion() { return baseItineraryVersion; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public String getProposalType() { return proposalType; }
    public ItineraryProposalStatus getStatus() { return status; }
    public ProposalDecisionMode getDecisionMode() { return decisionMode; }
    public int getDayNumber() { return dayNumber; }
    public Long getTargetItemId() { return targetItemId; }
    public String getReplacementPlaceId() { return replacementPlaceId; }
    public String getReplacementDisplayName() { return replacementDisplayName; }
    public LocalTime getReplacementStartTime() { return replacementStartTime; }
    public int getReplacementDurationMinutes() { return replacementDurationMinutes; }
    public String getCanonicalFingerprint() { return canonicalFingerprint; }
    public Long getAppliedItineraryId() { return appliedItineraryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
