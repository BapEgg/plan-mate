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
@Table(name = "itinerary_vote_ballots")
public class ItineraryVoteBallotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vote_id", nullable = false)
    private Long voteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BallotChoice choice;

    @Column(nullable = false)
    private boolean valid;

    @Column(name = "cast_at", nullable = false)
    private Instant castAt;

    protected ItineraryVoteBallotEntity() {
    }

    private ItineraryVoteBallotEntity(Long voteId, Long userId, BallotChoice choice, Instant now) {
        this.voteId = voteId;
        this.userId = userId;
        this.choice = choice;
        this.valid = true;
        this.castAt = now;
    }

    public static ItineraryVoteBallotEntity cast(Long voteId, Long userId, BallotChoice choice, Instant now) {
        return new ItineraryVoteBallotEntity(voteId, userId, choice, now);
    }

    public void change(BallotChoice choice, Instant now) {
        this.choice = choice;
        this.valid = true;
        this.castAt = now;
    }

    public void invalidate() {
        valid = false;
    }

    public Long getId() { return id; }
    public Long getVoteId() { return voteId; }
    public Long getUserId() { return userId; }
    public BallotChoice getChoice() { return choice; }
    public boolean isValid() { return valid; }
    public Instant getCastAt() { return castAt; }
}
