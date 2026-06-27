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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "itinerary_days",
        uniqueConstraints = @UniqueConstraint(name = "itinerary_days_itinerary_day_unique", columnNames = {"itinerary_id", "day"})
)
public class ItineraryDayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private ItineraryEntity itinerary;

    @Column(nullable = false)
    private int day;

    @Column(nullable = false)
    private LocalDate date;

    @OneToMany(mappedBy = "day")
    @OrderBy("sequence ASC")
    private List<ItineraryItemEntity> items = new ArrayList<>();

    protected ItineraryDayEntity() {
    }

    private ItineraryDayEntity(ItineraryEntity itinerary, int day, LocalDate date) {
        this.itinerary = itinerary;
        this.day = day;
        this.date = date;
    }

    public static ItineraryDayEntity create(ItineraryEntity itinerary, int day, LocalDate date) {
        return new ItineraryDayEntity(itinerary, day, date);
    }

    public Long getId() {
        return id;
    }

    public ItineraryEntity getItinerary() {
        return itinerary;
    }

    public int getDay() {
        return day;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<ItineraryItemEntity> getItems() {
        return List.copyOf(items);
    }
}
