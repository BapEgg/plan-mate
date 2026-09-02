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
    @JoinColumn(name = "generation_id")
    private ItineraryGenerationEntity generation;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int version;

    @Column(name = "base_itinerary_id")
    private Long baseItineraryId;

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "revision_source", length = 32)
    private String revisionSource;

    @Column(name = "revised_by_user_id")
    private Long revisedByUserId;

    @OneToMany(mappedBy = "itinerary")
    @OrderBy("day ASC")
    private List<ItineraryDayEntity> days = new ArrayList<>();

    protected ItineraryEntity() {
    }

    private ItineraryEntity(ItineraryGenerationEntity generation, Instant createdAt, int version) {
        this.tripId = generation.getTripId();
        this.generation = generation;
        this.createdAt = createdAt;
        this.version = version;
    }

    private ItineraryEntity(
            Long tripId,
            Instant createdAt,
            int version,
            Long baseItineraryId,
            Long proposalId,
            String revisionSource,
            Long revisedByUserId
    ) {
        this.tripId = tripId;
        this.createdAt = createdAt;
        this.version = version;
        this.baseItineraryId = baseItineraryId;
        this.proposalId = proposalId;
        this.revisionSource = revisionSource;
        this.revisedByUserId = revisedByUserId;
    }

    /**
     * ADR-0002: version은 trip 내 단조 증가하는 순번이다. 호출자가 "현재 trip의 최대 version + 1"을
     * 계산해 넘겨야 한다 (예: {@code ItineraryRepository.findMaxVersionByTripId}).
     */
    public static ItineraryEntity create(ItineraryGenerationEntity generation, Instant createdAt, int version) {
        return new ItineraryEntity(generation, createdAt, version);
    }

    public static ItineraryEntity createRevision(
            Long tripId,
            Instant createdAt,
            int version,
            Long baseItineraryId,
            Long proposalId,
            String revisionSource,
            Long revisedByUserId
    ) {
        return new ItineraryEntity(
                tripId,
                createdAt,
                version,
                baseItineraryId,
                proposalId,
                revisionSource,
                revisedByUserId
        );
    }

    public static ItineraryEntity createRegenerationRevision(
            ItineraryGenerationEntity generation,
            Instant createdAt,
            int version,
            Long baseItineraryId,
            String revisionSource,
            Long revisedByUserId
    ) {
        ItineraryEntity itinerary = new ItineraryEntity(generation, createdAt, version);
        itinerary.baseItineraryId = baseItineraryId;
        itinerary.revisionSource = revisionSource;
        itinerary.revisedByUserId = revisedByUserId;
        return itinerary;
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

    public int getVersion() {
        return version;
    }

    public Long getBaseItineraryId() {
        return baseItineraryId;
    }

    public Long getProposalId() {
        return proposalId;
    }

    public String getRevisionSource() {
        return revisionSource;
    }

    public Long getRevisedByUserId() {
        return revisedByUserId;
    }

    public List<ItineraryDayEntity> getDays() {
        return List.copyOf(days);
    }
}
