package com.planmate.itinerary.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;

@Entity
@Table(
        name = "itinerary_items",
        uniqueConstraints = @UniqueConstraint(name = "itinerary_items_day_sequence_unique", columnNames = {"day_id", "sequence"})
)
public class ItineraryItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDayEntity day;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "place_id", nullable = false, length = 255)
    private String placeId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_source", nullable = false, length = 40)
    private ItineraryItemCreatedSource createdSource;

    protected ItineraryItemEntity() {
    }

    private ItineraryItemEntity(
            ItineraryDayEntity day,
            int sequence,
            String placeId,
            LocalTime startTime,
            int durationMinutes,
            ItineraryItemCreatedSource createdSource
    ) {
        this.day = day;
        this.sequence = sequence;
        this.placeId = placeId;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.createdSource = createdSource == null ? ItineraryItemCreatedSource.AI_DRAFT : createdSource;
    }

    public static ItineraryItemEntity create(
            ItineraryDayEntity day,
            int sequence,
            String placeId,
            LocalTime startTime,
            int durationMinutes,
            ItineraryItemCreatedSource createdSource
    ) {
        return new ItineraryItemEntity(
                day,
                sequence,
                placeId,
                startTime,
                durationMinutes,
                createdSource
        );
    }

    public Long getId() {
        return id;
    }

    public ItineraryDayEntity getDay() {
        return day;
    }

    public int getSequence() {
        return sequence;
    }

    public String getPlaceId() {
        return placeId;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public ItineraryItemCreatedSource getCreatedSource() {
        return createdSource;
    }
}
