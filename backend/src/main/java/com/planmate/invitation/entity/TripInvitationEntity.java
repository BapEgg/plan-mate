package com.planmate.invitation.entity;

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
@Table(name = "trip_invitations")
public class TripInvitationEntity {

    public static final Duration VALIDITY = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "invitee_user_id", nullable = false)
    private Long inviteeUserId;

    @Column(name = "invited_by_user_id", nullable = false)
    private Long invitedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant respondedAt;

    protected TripInvitationEntity() {
    }

    private TripInvitationEntity(Long tripId, Long inviteeUserId, Long invitedByUserId, Instant now) {
        this.tripId = tripId;
        this.inviteeUserId = inviteeUserId;
        this.invitedByUserId = invitedByUserId;
        this.status = InvitationStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = now.plus(VALIDITY);
    }

    public static TripInvitationEntity create(Long tripId, Long inviteeUserId, Long invitedByUserId, Instant now) {
        return new TripInvitationEntity(tripId, inviteeUserId, invitedByUserId, now);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markExpired(Instant now) {
        this.status = InvitationStatus.EXPIRED;
        this.respondedAt = now;
    }

    public void accept(Instant now) {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void decline(Instant now) {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = now;
    }

    public void cancel(Instant now) {
        this.status = InvitationStatus.CANCELLED;
        this.respondedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getInviteeUserId() {
        return inviteeUserId;
    }

    public Long getInvitedByUserId() {
        return invitedByUserId;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
