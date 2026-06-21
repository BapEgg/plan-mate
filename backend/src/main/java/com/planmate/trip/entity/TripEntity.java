package com.planmate.trip.entity;

import com.planmate.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trips")
public class TripEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 60)
    private String destination;

    @Column(name = "destination_place_id", length = 255)
    private String destinationPlaceId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TripEntity() {
    }

    private TripEntity(
            String title,
            String destination,
            String destinationPlaceId,
            LocalDate startDate,
            LocalDate endDate,
            UserEntity createdBy,
            Instant now
    ) {
        this.title = title;
        this.destination = destination;
        this.destinationPlaceId = destinationPlaceId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static TripEntity create(
            String title,
            String destination,
            String destinationPlaceId,
            LocalDate startDate,
            LocalDate endDate,
            UserEntity createdBy,
            Instant now
    ) {
        return new TripEntity(title, destination, destinationPlaceId, startDate, endDate, createdBy, now);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDestination() {
        return destination;
    }

    public String getDestinationPlaceId() {
        return destinationPlaceId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
