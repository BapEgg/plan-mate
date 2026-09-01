package com.planmate.friend.entity;

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
@Table(name = "friend_requests")
public class FriendRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Column(name = "addressee_user_id", nullable = false)
    private Long addresseeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendRequestStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant respondedAt;

    protected FriendRequestEntity() {
    }

    private FriendRequestEntity(Long requesterUserId, Long addresseeUserId, Instant now) {
        this.requesterUserId = requesterUserId;
        this.addresseeUserId = addresseeUserId;
        this.status = FriendRequestStatus.PENDING;
        this.createdAt = now;
    }

    public static FriendRequestEntity create(Long requesterUserId, Long addresseeUserId, Instant now) {
        return new FriendRequestEntity(requesterUserId, addresseeUserId, now);
    }

    public boolean isPending() {
        return status == FriendRequestStatus.PENDING;
    }

    public void accept(Instant now) {
        this.status = FriendRequestStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void decline(Instant now) {
        this.status = FriendRequestStatus.DECLINED;
        this.respondedAt = now;
    }

    public void cancel(Instant now) {
        this.status = FriendRequestStatus.CANCELLED;
        this.respondedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getRequesterUserId() {
        return requesterUserId;
    }

    public Long getAddresseeUserId() {
        return addresseeUserId;
    }

    public FriendRequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
