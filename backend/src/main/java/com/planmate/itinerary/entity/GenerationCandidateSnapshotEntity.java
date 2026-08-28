package com.planmate.itinerary.entity;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
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
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "place_candidates",
        uniqueConstraints = {
                @UniqueConstraint(name = "place_candidates_generation_place_unique", columnNames = {"generation_id", "place_id"}),
                @UniqueConstraint(name = "place_candidates_generation_rank_unique", columnNames = {"generation_id", "rank"})
        }
)
public class GenerationCandidateSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private ItineraryGenerationEntity generation;

    @Column(name = "place_id", nullable = false, length = 255)
    private String placeId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "primary_type", length = 100)
    private String primaryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> types;

    @Column(name = "business_status", length = 40)
    private String businessStatus;

    @Column
    private Double rating;

    @Column(name = "user_rating_count")
    private Integer userRatingCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_categories", nullable = false, columnDefinition = "jsonb")
    private List<String> sourceCategories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_periods", nullable = false, columnDefinition = "jsonb")
    private List<String> openingPeriods;

    @Column(name = "forced_must_visit", nullable = false)
    private boolean forcedMustVisit;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private int rank;

    protected GenerationCandidateSnapshotEntity() {
    }

    private GenerationCandidateSnapshotEntity(
            ItineraryGenerationEntity generation,
            GenerationCandidateSnapshot snapshot
    ) {
        this.generation = generation;
        this.placeId = snapshot.placeId();
        this.name = snapshot.displayName();
        this.address = snapshot.formattedAddress();
        this.latitude = snapshot.location().latitude();
        this.longitude = snapshot.location().longitude();
        this.primaryType = snapshot.primaryType();
        this.types = List.copyOf(snapshot.types());
        this.businessStatus = snapshot.businessStatus();
        this.rating = snapshot.rating();
        this.userRatingCount = snapshot.userRatingCount();
        this.sourceCategories = List.copyOf(snapshot.sourceCategories());
        this.openingPeriods = List.copyOf(snapshot.openingPeriods());
        this.forcedMustVisit = snapshot.forcedMustVisit();
        this.distanceMeters = snapshot.distanceMeters();
        this.score = snapshot.score();
        this.rank = snapshot.rank();
    }

    public static GenerationCandidateSnapshotEntity from(
            ItineraryGenerationEntity generation,
            GenerationCandidateSnapshot snapshot
    ) {
        return new GenerationCandidateSnapshotEntity(generation, snapshot);
    }

    public Long getId() {
        return id;
    }

    public ItineraryGenerationEntity getGeneration() {
        return generation;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public List<String> getTypes() {
        return List.copyOf(types);
    }

    public String getBusinessStatus() {
        return businessStatus;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getUserRatingCount() {
        return userRatingCount;
    }

    public List<String> getSourceCategories() {
        return List.copyOf(sourceCategories);
    }

    public List<String> getOpeningPeriods() {
        return List.copyOf(openingPeriods);
    }

    public boolean isForcedMustVisit() {
        return forcedMustVisit;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public Double getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }
}
