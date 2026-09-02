package com.planmate.vote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "itinerary_votes")
public class ItineraryVoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "proposal_id", nullable = false, unique = true)
    private Long proposalId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VoteStatus status;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "result_reason", length = 64)
    private String resultReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItineraryVoteEntity() {
    }

    private ItineraryVoteEntity(Long tripId, Long proposalId, Long createdByUserId, Instant deadline, Instant now) {
        this.tripId = tripId;
        this.proposalId = proposalId;
        this.createdByUserId = createdByUserId;
        this.status = VoteStatus.OPEN;
        this.deadline = deadline;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryVoteEntity open(
            Long tripId,
            Long proposalId,
            Long createdByUserId,
            Instant deadline,
            Instant now
    ) {
        return new ItineraryVoteEntity(tripId, proposalId, createdByUserId, deadline, now);
    }

    public void close(VoteStatus result, String reason, Instant now) {
        if (status != VoteStatus.OPEN) {
            return;
        }
        if (result == VoteStatus.OPEN) {
            throw new IllegalArgumentException("A vote cannot close as OPEN");
        }
        status = result;
        resultReason = reason;
        closedAt = now;
        updatedAt = now;
    }

    public boolean isOpen() { return status == VoteStatus.OPEN; }
    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getProposalId() { return proposalId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public VoteStatus getStatus() { return status; }
    public Instant getDeadline() { return deadline; }
    public Instant getClosedAt() { return closedAt; }
    public String getResultReason() { return resultReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
