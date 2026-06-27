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
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "destination_formatted_address", length = 255)
    private String destinationFormattedAddress;

    @Column(name = "destination_latitude")
    private Double destinationLatitude;

    @Column(name = "destination_longitude")
    private Double destinationLongitude;

    @Column(name = "destination_viewport_low_latitude")
    private Double destinationViewportLowLatitude;

    @Column(name = "destination_viewport_low_longitude")
    private Double destinationViewportLowLongitude;

    @Column(name = "destination_viewport_high_latitude")
    private Double destinationViewportHighLatitude;

    @Column(name = "destination_viewport_high_longitude")
    private Double destinationViewportHighLongitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "destination_types", columnDefinition = "jsonb")
    private List<String> destinationTypes;

    @Column(name = "destination_primary_type", length = 100)
    private String destinationPrimaryType;

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
            String destinationFormattedAddress,
            Double destinationLatitude,
            Double destinationLongitude,
            Double destinationViewportLowLatitude,
            Double destinationViewportLowLongitude,
            Double destinationViewportHighLatitude,
            Double destinationViewportHighLongitude,
            List<String> destinationTypes,
            String destinationPrimaryType,
            LocalDate startDate,
            LocalDate endDate,
            UserEntity createdBy,
            Instant now
    ) {
        this.title = title;
        this.destination = destination;
        this.destinationPlaceId = destinationPlaceId;
        this.destinationFormattedAddress = destinationFormattedAddress;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.destinationViewportLowLatitude = destinationViewportLowLatitude;
        this.destinationViewportLowLongitude = destinationViewportLowLongitude;
        this.destinationViewportHighLatitude = destinationViewportHighLatitude;
        this.destinationViewportHighLongitude = destinationViewportHighLongitude;
        this.destinationTypes = destinationTypes == null ? List.of() : List.copyOf(destinationTypes);
        this.destinationPrimaryType = destinationPrimaryType;
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
            String destinationFormattedAddress,
            Double destinationLatitude,
            Double destinationLongitude,
            Double destinationViewportLowLatitude,
            Double destinationViewportLowLongitude,
            Double destinationViewportHighLatitude,
            Double destinationViewportHighLongitude,
            List<String> destinationTypes,
            String destinationPrimaryType,
            LocalDate startDate,
            LocalDate endDate,
            UserEntity createdBy,
            Instant now
    ) {
        return new TripEntity(
                title,
                destination,
                destinationPlaceId,
                destinationFormattedAddress,
                destinationLatitude,
                destinationLongitude,
                destinationViewportLowLatitude,
                destinationViewportLowLongitude,
                destinationViewportHighLatitude,
                destinationViewportHighLongitude,
                destinationTypes,
                destinationPrimaryType,
                startDate,
                endDate,
                createdBy,
                now
        );
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

    public String getDestinationFormattedAddress() {
        return destinationFormattedAddress;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public Double getDestinationViewportLowLatitude() {
        return destinationViewportLowLatitude;
    }

    public Double getDestinationViewportLowLongitude() {
        return destinationViewportLowLongitude;
    }

    public Double getDestinationViewportHighLatitude() {
        return destinationViewportHighLatitude;
    }

    public Double getDestinationViewportHighLongitude() {
        return destinationViewportHighLongitude;
    }

    public List<String> getDestinationTypes() {
        return destinationTypes == null ? List.of() : List.copyOf(destinationTypes);
    }

    public String getDestinationPrimaryType() {
        return destinationPrimaryType;
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
