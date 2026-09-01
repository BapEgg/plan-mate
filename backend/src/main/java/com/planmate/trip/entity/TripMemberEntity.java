package com.planmate.trip.entity;

import com.planmate.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trip_members")
public class TripMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LeftReason leftReason;

    protected TripMemberEntity() {
    }

    private TripMemberEntity(TripEntity trip, UserEntity user, TripMemberRole role, Instant createdAt) {
        this.trip = trip;
        this.user = user;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
        this.createdAt = createdAt;
        this.joinedAt = createdAt;
    }

    public static TripMemberEntity owner(TripEntity trip, UserEntity user, Instant now) {
        return new TripMemberEntity(trip, user, TripMemberRole.OWNER, now);
    }

    public static TripMemberEntity member(TripEntity trip, UserEntity user, Instant now) {
        return new TripMemberEntity(trip, user, TripMemberRole.MEMBER, now);
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    /**
     * ADR-0001: 나가기/내보내기는 이 행을 종료 상태로 갱신할 뿐, 새 interval을 만들지 않는다.
     * 재가입은 새 {@link TripMemberEntity} 행으로 표현한다.
     */
    public void end(LeftReason reason, Instant now) {
        if (status != MembershipStatus.ACTIVE) {
            throw new IllegalStateException("Cannot end a membership interval that is not ACTIVE: " + status);
        }
        this.status = reason == LeftReason.REMOVED ? MembershipStatus.REMOVED : MembershipStatus.LEFT;
        this.leftAt = now;
        this.leftReason = reason;
    }

    /** 방장 이전: 같은 interval의 role만 바꾼다(interval을 종료하지 않는다). */
    public void changeRole(TripMemberRole role) {
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public TripEntity getTrip() {
        return trip;
    }

    public UserEntity getUser() {
        return user;
    }

    public TripMemberRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public LeftReason getLeftReason() {
        return leftReason;
    }

}
