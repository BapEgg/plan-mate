package com.planmate.itinerary.entity;

import com.planmate.trip.entity.TripEntity;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "itineraries")
public class ItineraryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private ItineraryGenerationEntity generation;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itinerary")
    @OrderBy("day ASC")
    private List<ItineraryDayEntity> days = new ArrayList<>();

    protected ItineraryEntity() {
    }

    private ItineraryEntity(TripEntity trip, ItineraryGenerationEntity generation, String summary, Instant createdAt) {
        this.trip = trip;
        this.generation = generation;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public static ItineraryEntity create(TripEntity trip, ItineraryGenerationEntity generation, String summary, Instant createdAt) {
        return new ItineraryEntity(trip, generation, summary, createdAt);
    }

    public Long getId() {
        return id;
    }

    public TripEntity getTrip() {
        return trip;
    }

    public ItineraryGenerationEntity getGeneration() {
        return generation;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ItineraryDayEntity> getDays() {
        return List.copyOf(days);
    }
}
