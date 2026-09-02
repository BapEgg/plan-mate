package com.planmate.itinerary.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "route_leg_snapshots")
public class RouteLegSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "travel_mode", nullable = false, length = 20)
    private String travelMode;

    @Column(name = "cache_key", nullable = false, length = 120)
    private String cacheKey;

    @Column(name = "origin_latitude", nullable = false)
    private double originLatitude;

    @Column(name = "origin_longitude", nullable = false)
    private double originLongitude;

    @Column(name = "destination_latitude", nullable = false)
    private double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false)
    private double destinationLongitude;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "geometry", columnDefinition = "TEXT")
    private String geometry;

    protected RouteLegSnapshotEntity() {
    }

    private RouteLegSnapshotEntity(
            String travelMode,
            String cacheKey,
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            int distanceMeters,
            int durationSeconds,
            String provider,
            Instant verifiedAt,
            String geometry
    ) {
        this.travelMode = travelMode;
        this.cacheKey = cacheKey;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.provider = provider;
        this.verifiedAt = verifiedAt;
        this.geometry = geometry;
    }

    public static RouteLegSnapshotEntity create(
            String travelMode,
            String cacheKey,
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            int distanceMeters,
            int durationSeconds,
            String provider,
            Instant verifiedAt,
            String geometry
    ) {
        return new RouteLegSnapshotEntity(
                travelMode,
                cacheKey,
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude,
                distanceMeters,
                durationSeconds,
                provider,
                verifiedAt,
                geometry
        );
    }

    public void refresh(int distanceMeters, int durationSeconds, String provider, Instant verifiedAt, String geometry) {
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.provider = provider;
        this.verifiedAt = verifiedAt;
        this.geometry = geometry;
    }

    public Long getId() {
        return id;
    }

    public String getTravelMode() {
        return travelMode;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getProvider() {
        return provider;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getGeometry() {
        return geometry;
    }
}
