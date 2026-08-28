package com.planmate.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "itineraries",
        uniqueConstraints = @UniqueConstraint(
                name = "itineraries_generation_unique",
                columnNames = "generation_id"
        )
)
public class ItineraryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private ItineraryGenerationEntity generation;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itinerary")
    @OrderBy("day ASC")
    private List<ItineraryDayEntity> days = new ArrayList<>();

    protected ItineraryEntity() {
    }

    private ItineraryEntity(ItineraryGenerationEntity generation, Instant createdAt) {
        this.tripId = generation.getTripId();
        this.generation = generation;
        this.createdAt = createdAt;
    }

    public static ItineraryEntity create(ItineraryGenerationEntity generation, Instant createdAt) {
        return new ItineraryEntity(generation, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public ItineraryGenerationEntity getGeneration() {
        return generation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ItineraryDayEntity> getDays() {
        return List.copyOf(days);
    }
}
