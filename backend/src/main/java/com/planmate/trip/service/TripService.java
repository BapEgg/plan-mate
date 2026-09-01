package com.planmate.trip.service;

import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.PlaceDetailsResolver;
import com.planmate.place.api.ResolvedPlace;
import com.planmate.trip.api.TripDetailTrip;
import com.planmate.trip.api.TripDetailTripReader;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.ResolvedAccommodation;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripStatus;
import com.planmate.trip.dto.TripSummaryResponse;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.InvalidTripRequestException;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService implements TripDetailTripReader {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final TripCreationPersistenceService tripCreationPersistenceService;
    private final PlaceDetailsResolver placeDetailsResolver;
    private final SchedulePreferenceResolver schedulePreferenceResolver;
    private final Clock clock;

    public TripService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            TripCreationPersistenceService tripCreationPersistenceService,
            PlaceDetailsResolver placeDetailsResolver,
            SchedulePreferenceResolver schedulePreferenceResolver,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.tripCreationPersistenceService = tripCreationPersistenceService;
        this.placeDetailsResolver = placeDetailsResolver;
        this.schedulePreferenceResolver = schedulePreferenceResolver;
        this.clock = clock;
    }

    public TripSummaryResponse create(Long userId, TripCreateRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.startDate().isBefore(today) || request.endDate().isBefore(today)) {
            throw new InvalidTripRequestException("여행 날짜는 오늘 또는 이후로 선택해 주세요.");
        }
        String destinationPlaceId = request.destinationPlaceId().trim();
        ResolvedPlace destination = placeDetailsResolver.resolve(destinationPlaceId, "ko");
        ResolvedAccommodation accommodation = resolveAccommodation(request.accommodation());
        List<MustVisitPlaceSnapshot> mustVisitPlaces = resolveMustVisitPlaces(request.additionalRequest().mustVisitPlaceIds());
        ResolvedSchedulePreference schedulePreference = schedulePreferenceResolver.resolve(request.schedulePreference());
        TripEntity trip = tripCreationPersistenceService.create(
                userId,
                request,
                destination,
                accommodation,
                mustVisitPlaces,
                schedulePreference
        );

        return toSummaryResponse(trip, 1);
    }

    private ResolvedAccommodation resolveAccommodation(TripCreateRequest.AccommodationRequest request) {
        if (request.mode() != AccommodationMode.PLACE_SEARCH) {
            return null;
        }
        ResolvedPlace place = placeDetailsResolver.resolve(request.placeId().trim(), "ko");
        GeoPoint location = place.location();
        if (location == null) {
            throw new InvalidTripRequestException("선택한 숙소의 위치 정보를 확인할 수 없습니다.");
        }
        return new ResolvedAccommodation(
                place.placeId(),
                place.displayName(),
                place.formattedAddress(),
                location.latitude(),
                location.longitude(),
                place.types(),
                place.primaryType()
        );
    }

    private List<MustVisitPlaceSnapshot> resolveMustVisitPlaces(List<String> placeIds) {
        return placeIds.stream()
                .map(String::trim)
                .filter(placeId -> !placeId.isBlank())
                .map(placeId -> {
                    ResolvedPlace place = placeDetailsResolver.resolve(placeId, "ko");
                    GeoPoint location = place.location();
                    if (location == null) {
                        throw new InvalidTripRequestException("꼭 가보고 싶은 장소의 위치 정보를 확인할 수 없습니다.");
                    }
                    return new MustVisitPlaceSnapshot(
                            place.placeId(),
                            place.displayName(),
                            place.formattedAddress(),
                            location.latitude(),
                            location.longitude(),
                            place.types(),
                            place.primaryType()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> listMine(Long userId) {
        return tripMemberRepository.findByUser_IdAndStatusOrderByTrip_CreatedAtDesc(userId, MembershipStatus.ACTIVE)
                .stream()
                .map(member -> {
                    TripEntity trip = member.getTrip();
                    return toSummaryResponse(trip, tripMemberRepository.countByTrip_IdAndStatus(trip.getId(), MembershipStatus.ACTIVE));
                })
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long tripId) {
        TripEntity trip = tripRepository.findByIdAndCreatedBy_Id(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        tripRepository.delete(trip);
    }

    @Transactional(readOnly = true)
    @Override
    public TripDetailTrip getAccessibleTrip(Long userId, Long tripId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        List<TripMemberEntity> members = tripMemberRepository.findByTrip_IdAndStatusOrderByCreatedAtAsc(trip.getId(), MembershipStatus.ACTIVE);
        List<TripDetailTrip.Member> memberResponses = members.stream()
                .map(member -> new TripDetailTrip.Member(
                        member.getUser().getId(),
                        member.getUser().getNickname(),
                        member.getUser().getProfileImageUrl(),
                        member.getRole().name()
                ))
                .toList();

        return new TripDetailTrip(
                trip.getId(),
                trip.getTitle(),
                trip.getDestination(),
                trip.getDestinationPlaceId(),
                trip.getStartDate(),
                trip.getEndDate(),
                statusOf(trip),
                members.size(),
                trip.getCreatedAt(),
                memberResponses,
                toDestinationInfo(trip),
                tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                        .map(this::toPlanningProfile)
                        .orElse(null),
                trip.getTimezone()
        );
    }

    private TripSummaryResponse toSummaryResponse(TripEntity trip, long memberCount) {
        return new TripSummaryResponse(
                trip.getId().toString(),
                trip.getTitle(),
                trip.getDestination(),
                trip.getDestinationPlaceId(),
                trip.getStartDate(),
                trip.getEndDate(),
                statusOf(trip),
                memberCount,
                trip.getCreatedAt()
        );
    }

    private TripStatus statusOf(TripEntity trip) {
        LocalDate today = LocalDate.now(clock);
        if (trip.getEndDate().isBefore(today)) {
            return TripStatus.COMPLETED;
        }
        if (trip.getStartDate().isAfter(today)) {
            return TripStatus.UPCOMING;
        }
        return TripStatus.PLANNING;
    }

    private TripDetailTrip.DestinationInfo toDestinationInfo(TripEntity trip) {
        return new TripDetailTrip.DestinationInfo(
                trip.getDestinationPlaceId(),
                trip.getDestination(),
                trip.getDestinationFormattedAddress(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getDestinationViewportLowLatitude(),
                trip.getDestinationViewportLowLongitude(),
                trip.getDestinationViewportHighLatitude(),
                trip.getDestinationViewportHighLongitude(),
                trip.getDestinationTypes(),
                trip.getDestinationPrimaryType()
        );
    }

    private TripDetailTrip.PlanningProfile toPlanningProfile(TripPlanningProfileEntity profile) {
        return new TripDetailTrip.PlanningProfile(
                profile.getCompanionCount(),
                profile.getCompanionType(),
                profile.isHasChildren(),
                profile.getChildCount(),
                profile.getChildAgeGroup(),
                profile.isHasSeniors(),
                profile.getSeniorCount(),
                profile.getCurrencyCode(),
                profile.getBudgetAmount(),
                profile.getBudgetLevel(),
                profile.getIncludedBudgetItems(),
                profile.getTravelPace(),
                profile.getInterests(),
                profile.getPrimaryTransportMode(),
                profile.getSecondaryTransportModes(),
                profile.getAccommodationMode(),
                profile.getAccommodationArea(),
                profile.getAccommodationName(),
                profile.getAccommodationPlaceId(),
                profile.getAccommodationFormattedAddress(),
                profile.getAccommodationLatitude(),
                profile.getAccommodationLongitude(),
                profile.getAccommodationTypes(),
                profile.getAccommodationPrimaryType(),
                profile.getCheckInTime(),
                profile.getCheckOutTime(),
                profile.getDailyStartTime(),
                profile.getDailyEndTime(),
                profile.getMustVisitPlaces(),
                profile.getAvoidConditions(),
                profile.getFreeRequest()
        );
    }

}
