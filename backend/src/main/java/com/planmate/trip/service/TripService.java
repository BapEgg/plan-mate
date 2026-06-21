package com.planmate.trip.service;

import com.planmate.place.service.GooglePlacesService;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripDetailResponse;
import com.planmate.trip.dto.TripMemberResponse;
import com.planmate.trip.dto.TripStatus;
import com.planmate.trip.dto.TripSummaryResponse;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.exception.UserNotFoundException;
import com.planmate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final GooglePlacesService googlePlacesService;
    private final Clock clock;

    public TripService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            UserRepository userRepository,
            GooglePlacesService googlePlacesService,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.userRepository = userRepository;
        this.googlePlacesService = googlePlacesService;
        this.clock = clock;
    }

    @Transactional
    public TripSummaryResponse create(Long userId, TripCreateRequest request) {
        UserEntity owner = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Instant now = Instant.now(clock);
        String destinationPlaceId = request.destinationPlaceId().trim();

        googlePlacesService.validatePlaceId(destinationPlaceId);

        TripEntity trip = tripRepository.save(TripEntity.create(
                request.title().trim(),
                request.destination().trim(),
                destinationPlaceId,
                request.startDate(),
                request.endDate(),
                owner,
                now
        ));
        tripMemberRepository.save(TripMemberEntity.owner(trip, owner, now));

        return toSummaryResponse(trip, 1);
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> listMine(Long userId) {
        return tripMemberRepository.findByUser_IdOrderByTrip_CreatedAtDesc(userId)
                .stream()
                .map(member -> {
                    TripEntity trip = member.getTrip();
                    return toSummaryResponse(trip, tripMemberRepository.countByTrip_Id(trip.getId()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getDetail(Long userId, Long tripId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        List<TripMemberEntity> members = tripMemberRepository.findByTrip_IdOrderByCreatedAtAsc(trip.getId());
        List<TripMemberResponse> memberResponses = members.stream()
                .map(member -> new TripMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getNickname(),
                        member.getUser().getProfileImageUrl(),
                        member.getRole()
                ))
                .toList();

        return new TripDetailResponse(
                trip.getId().toString(),
                trip.getTitle(),
                trip.getDestination(),
                trip.getDestinationPlaceId(),
                trip.getStartDate(),
                trip.getEndDate(),
                statusOf(trip),
                members.size(),
                trip.getCreatedAt(),
                memberResponses
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

}
