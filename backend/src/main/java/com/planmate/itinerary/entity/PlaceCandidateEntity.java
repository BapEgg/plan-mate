package com.planmate.itinerary.entity;

import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
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
        uniqueConstraints = @UniqueConstraint(name = "place_candidates_generation_place_unique", columnNames = {"generation_id", "place_id"})
)
public class PlaceCandidateEntity {

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

    @Column
    private Double rating;

    @Column(name = "user_rating_count")
    private Integer userRatingCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_categories", nullable = false, columnDefinition = "jsonb")
    private List<CandidateSearchCategory> sourceCategories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_periods", nullable = false, columnDefinition = "jsonb")
    private List<String> openingPeriods;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private int rank;

    protected PlaceCandidateEntity() {
    }

    private PlaceCandidateEntity(ItineraryGenerationEntity generation, CollectedPlaceCandidate candidate, int rank) {
        this.generation = generation;
        this.placeId = candidate.placeId();
        this.name = candidate.name();
        this.address = candidate.address();
        this.latitude = candidate.location().latitude();
        this.longitude = candidate.location().longitude();
        this.primaryType = candidate.primaryType();
        this.rating = candidate.rating();
        this.userRatingCount = candidate.userRatingCount();
        this.sourceCategories = List.copyOf(candidate.sourceCategories());
        this.openingPeriods = List.copyOf(candidate.openingPeriods());
        this.score = candidate.score();
        this.rank = rank;
    }

    public static PlaceCandidateEntity from(ItineraryGenerationEntity generation, CollectedPlaceCandidate candidate, int rank) {
        return new PlaceCandidateEntity(generation, candidate, rank);
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

    public Double getRating() {
        return rating;
    }

    public Integer getUserRatingCount() {
        return userRatingCount;
    }

    public List<CandidateSearchCategory> getSourceCategories() {
        return List.copyOf(sourceCategories);
    }

    public List<String> getOpeningPeriods() {
        return List.copyOf(openingPeriods);
    }

    public Double getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }
}
