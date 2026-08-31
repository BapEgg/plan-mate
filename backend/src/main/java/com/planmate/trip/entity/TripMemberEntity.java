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

    @Column(nullable = false)
    private Instant createdAt;

    protected TripMemberEntity() {
    }

    private TripMemberEntity(TripEntity trip, UserEntity user, TripMemberRole role, Instant createdAt) {
        this.trip = trip;
        this.user = user;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static TripMemberEntity owner(TripEntity trip, UserEntity user, Instant now) {
        return new TripMemberEntity(trip, user, TripMemberRole.OWNER, now);
    }

    public static TripMemberEntity member(TripEntity trip, UserEntity user, Instant now) {
        return new TripMemberEntity(trip, user, TripMemberRole.MEMBER, now);
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

}
