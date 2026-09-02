package com.planmate.vote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "itinerary_vote_voters")
public class ItineraryVoteVoterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vote_id", nullable = false)
    private Long voteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean valid;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected ItineraryVoteVoterEntity() {
    }

    private ItineraryVoteVoterEntity(Long voteId, Long userId) {
        this.voteId = voteId;
        this.userId = userId;
        this.valid = true;
    }

    public static ItineraryVoteVoterEntity create(Long voteId, Long userId) {
        return new ItineraryVoteVoterEntity(voteId, userId);
    }

    public void invalidate(Instant now) {
        valid = false;
        invalidatedAt = now;
    }

    public Long getId() { return id; }
    public Long getVoteId() { return voteId; }
    public Long getUserId() { return userId; }
    public boolean isValid() { return valid; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
}
