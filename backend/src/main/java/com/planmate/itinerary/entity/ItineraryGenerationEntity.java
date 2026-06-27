package com.planmate.itinerary.entity;

import com.planmate.trip.entity.TripEntity;
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
@Table(name = "itinerary_generations")
public class ItineraryGenerationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ItineraryGenerationStatus status;

    @Column(nullable = false, length = 80)
    private String promptVersion;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ItineraryGenerationEntity() {
    }

    private ItineraryGenerationEntity(TripEntity trip, String promptVersion, Instant now) {
        this.trip = trip;
        this.status = ItineraryGenerationStatus.CREATED;
        this.promptVersion = promptVersion;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryGenerationEntity create(TripEntity trip, String promptVersion, Instant now) {
        return new ItineraryGenerationEntity(trip, promptVersion, now);
    }

    public void markCollecting(Instant now) {
        status = ItineraryGenerationStatus.COLLECTING_CANDIDATES;
        failureReason = null;
        updatedAt = now;
    }

    public void markReady(Instant now) {
        status = ItineraryGenerationStatus.READY_FOR_PLANNING;
        failureReason = null;
        updatedAt = now;
    }

    public void markValidating(Instant now) {
        status = ItineraryGenerationStatus.VALIDATING;
        updatedAt = now;
    }

    public void markCompleted(Instant now) {
        status = ItineraryGenerationStatus.COMPLETED;
        failureReason = null;
        updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        status = ItineraryGenerationStatus.FAILED;
        failureReason = reason;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public TripEntity getTrip() {
        return trip;
    }

    public ItineraryGenerationStatus getStatus() {
        return status;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
