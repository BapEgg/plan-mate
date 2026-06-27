package com.planmate.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "place_name", nullable = false, length = 200)
    private String placeName;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(length = 500)
    private String reason;

    protected ItineraryItemEntity() {
    }

    private ItineraryItemEntity(
            ItineraryDayEntity day,
            int sequence,
            String placeId,
            String placeName,
            Double latitude,
            Double longitude,
            LocalTime startTime,
            int durationMinutes,
            String reason
    ) {
        this.day = day;
        this.sequence = sequence;
        this.placeId = placeId;
        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.reason = reason;
    }

    public static ItineraryItemEntity create(
            ItineraryDayEntity day,
            int sequence,
            PlaceCandidateEntity candidate,
            LocalTime startTime,
            int durationMinutes,
            String reason
    ) {
        return new ItineraryItemEntity(
                day,
                sequence,
                candidate.getPlaceId(),
                candidate.getName(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                startTime,
                durationMinutes,
                reason
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

    public String getPlaceName() {
        return placeName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getReason() {
        return reason;
    }
}
