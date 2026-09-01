package com.planmate.membership.entity;

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
@Table(name = "owner_transfer_requests")
public class OwnerTransferRequestEntity {

    public static final Duration VALIDITY = Duration.ofHours(48);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnerTransferRequestStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant respondedAt;

    protected OwnerTransferRequestEntity() {
    }

    private OwnerTransferRequestEntity(Long tripId, Long fromUserId, Long toUserId, Instant now) {
        this.tripId = tripId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.status = OwnerTransferRequestStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = now.plus(VALIDITY);
    }

    public static OwnerTransferRequestEntity create(Long tripId, Long fromUserId, Long toUserId, Instant now) {
        return new OwnerTransferRequestEntity(tripId, fromUserId, toUserId, now);
    }

    public boolean isPending() {
        return status == OwnerTransferRequestStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markExpired(Instant now) {
        this.status = OwnerTransferRequestStatus.EXPIRED;
        this.respondedAt = now;
    }

    public void accept(Instant now) {
        this.status = OwnerTransferRequestStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void decline(Instant now) {
        this.status = OwnerTransferRequestStatus.DECLINED;
        this.respondedAt = now;
    }

    public void cancel(Instant now) {
        this.status = OwnerTransferRequestStatus.CANCELLED;
        this.respondedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public OwnerTransferRequestStatus getStatus() {
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
