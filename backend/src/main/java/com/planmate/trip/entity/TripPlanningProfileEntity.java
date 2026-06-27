package com.planmate.trip.entity;

import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.ChildAgeGroup;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.ResolvedAccommodation;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.dto.TripCreateRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trip_planning_profiles")
public class TripPlanningProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private TripEntity trip;

    @Column(nullable = false)
    private int companionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanionType companionType;

    @Column(nullable = false)
    private boolean hasChildren;

    @Column(nullable = false)
    private int childCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ChildAgeGroup childAgeGroup;

    @Column(nullable = false)
    private boolean hasSeniors;

    @Column(nullable = false)
    private int seniorCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currencyCode;

    @Column
    private Long budgetAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BudgetLevel budgetLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<BudgetItem> includedBudgetItems;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TravelPace travelPace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TripInterest> interests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransportMode primaryTransportMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TransportMode> secondaryTransportModes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccommodationMode accommodationMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AccommodationArea accommodationArea;

    @Column(length = 120)
    private String accommodationName;

    @Column(length = 255)
    private String accommodationPlaceId;

    @Column(length = 255)
    private String accommodationFormattedAddress;

    @Column
    private Double accommodationLatitude;

    @Column
    private Double accommodationLongitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> accommodationTypes;

    @Column(length = 100)
    private String accommodationPrimaryType;

    @Column
    private LocalTime checkInTime;

    @Column
    private LocalTime checkOutTime;

    @Column(nullable = false)
    private LocalTime dailyStartTime;

    @Column(nullable = false)
    private LocalTime dailyEndTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<MustVisitPlaceSnapshot> mustVisitPlaces;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<AvoidCondition> avoidConditions;

    @Column(length = 800)
    private String freeRequest;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TripPlanningProfileEntity() {
    }

    private TripPlanningProfileEntity(
            TripEntity trip,
            TripCreateRequest request,
            ResolvedAccommodation resolvedAccommodation,
            List<MustVisitPlaceSnapshot> resolvedMustVisitPlaces,
            ResolvedSchedulePreference schedulePreference,
            Instant now
    ) {
        TripCreateRequest.CompanionRequest companion = request.companion();
        TripCreateRequest.BudgetRequest budget = request.budget();
        TripCreateRequest.PreferenceRequest preferences = request.preferences();
        TripCreateRequest.TransportationRequest transportation = request.transportation();
        TripCreateRequest.AccommodationRequest accommodation = request.accommodation();
        TripCreateRequest.AdditionalRequest additionalRequest = request.additionalRequest();

        this.trip = trip;
        this.companionCount = companion.count();
        this.companionType = companion.type();
        this.hasChildren = companion.hasChildren();
        this.childCount = companion.normalizedChildCount();
        this.childAgeGroup = companion.hasChildren() ? companion.childAgeGroup() : null;
        this.hasSeniors = companion.hasSeniors();
        this.seniorCount = companion.normalizedSeniorCount();
        this.currencyCode = budget.currencyCode();
        this.budgetAmount = budget.amount();
        this.budgetLevel = budget.level();
        this.includedBudgetItems = List.copyOf(budget.includedItems());
        this.travelPace = preferences.travelPace();
        this.interests = List.copyOf(preferences.interests());
        this.primaryTransportMode = transportation.primaryMode();
        this.secondaryTransportModes = List.copyOf(transportation.secondaryModes());
        this.accommodationMode = accommodation.mode();
        this.accommodationArea = accommodation.mode() == AccommodationMode.UNDECIDED ? accommodation.preferredArea() : null;
        this.accommodationName = resolvedAccommodation == null ? null : resolvedAccommodation.name();
        this.accommodationPlaceId = resolvedAccommodation == null ? null : resolvedAccommodation.placeId();
        this.accommodationFormattedAddress = resolvedAccommodation == null ? null : resolvedAccommodation.formattedAddress();
        this.accommodationLatitude = resolvedAccommodation == null ? null : resolvedAccommodation.latitude();
        this.accommodationLongitude = resolvedAccommodation == null ? null : resolvedAccommodation.longitude();
        this.accommodationTypes = resolvedAccommodation == null ? List.of() : List.copyOf(resolvedAccommodation.types());
        this.accommodationPrimaryType = resolvedAccommodation == null ? null : resolvedAccommodation.primaryType();
        this.checkInTime = accommodation.checkInTime();
        this.checkOutTime = accommodation.checkOutTime();
        this.dailyStartTime = schedulePreference.dailyStartTime();
        this.dailyEndTime = schedulePreference.dailyEndTime();
        this.mustVisitPlaces = List.copyOf(resolvedMustVisitPlaces);
        this.avoidConditions = List.copyOf(additionalRequest.avoidConditions());
        this.freeRequest = normalize(additionalRequest.freeRequest());
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static TripPlanningProfileEntity create(
            TripEntity trip,
            TripCreateRequest request,
            ResolvedAccommodation resolvedAccommodation,
            List<MustVisitPlaceSnapshot> resolvedMustVisitPlaces,
            ResolvedSchedulePreference schedulePreference,
            Instant now
    ) {
        return new TripPlanningProfileEntity(
                trip,
                request,
                resolvedAccommodation,
                resolvedMustVisitPlaces,
                schedulePreference,
                now
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public TripEntity getTrip() {
        return trip;
    }

    public int getCompanionCount() {
        return companionCount;
    }

    public CompanionType getCompanionType() {
        return companionType;
    }

    public boolean isHasChildren() {
        return hasChildren;
    }

    public int getChildCount() {
        return childCount;
    }

    public ChildAgeGroup getChildAgeGroup() {
        return childAgeGroup;
    }

    public boolean isHasSeniors() {
        return hasSeniors;
    }

    public int getSeniorCount() {
        return seniorCount;
    }

    public CurrencyCode getCurrencyCode() {
        return currencyCode;
    }

    public Long getBudgetAmount() {
        return budgetAmount;
    }

    public BudgetLevel getBudgetLevel() {
        return budgetLevel;
    }

    public List<BudgetItem> getIncludedBudgetItems() {
        return List.copyOf(includedBudgetItems);
    }

    public TravelPace getTravelPace() {
        return travelPace;
    }

    public List<TripInterest> getInterests() {
        return List.copyOf(interests);
    }

    public TransportMode getPrimaryTransportMode() {
        return primaryTransportMode;
    }

    public List<TransportMode> getSecondaryTransportModes() {
        return List.copyOf(secondaryTransportModes);
    }

    public AccommodationMode getAccommodationMode() {
        return accommodationMode;
    }

    public AccommodationArea getAccommodationArea() {
        return accommodationArea;
    }

    public String getAccommodationName() {
        return accommodationName;
    }

    public String getAccommodationPlaceId() {
        return accommodationPlaceId;
    }

    public String getAccommodationFormattedAddress() {
        return accommodationFormattedAddress;
    }

    public Double getAccommodationLatitude() {
        return accommodationLatitude;
    }

    public Double getAccommodationLongitude() {
        return accommodationLongitude;
    }

    public List<String> getAccommodationTypes() {
        return List.copyOf(accommodationTypes);
    }

    public String getAccommodationPrimaryType() {
        return accommodationPrimaryType;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public LocalTime getDailyStartTime() {
        return dailyStartTime;
    }

    public LocalTime getDailyEndTime() {
        return dailyEndTime;
    }

    public List<MustVisitPlaceSnapshot> getMustVisitPlaces() {
        return List.copyOf(mustVisitPlaces);
    }

    public List<AvoidCondition> getAvoidConditions() {
        return List.copyOf(avoidConditions);
    }

    public String getFreeRequest() {
        return freeRequest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
