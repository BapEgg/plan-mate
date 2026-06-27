package com.planmate.trip.service;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.ResolvedAccommodation;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.exception.UserNotFoundException;
import com.planmate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripCreationPersistenceService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TripCreationPersistenceService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TripEntity create(
            Long userId,
            TripCreateRequest request,
            ResolvedDestination destination,
            ResolvedAccommodation accommodation,
            List<MustVisitPlaceSnapshot> mustVisitPlaces,
            ResolvedSchedulePreference schedulePreference
    ) {
        UserEntity owner = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Instant now = Instant.now(clock);
        GeoPoint location = destination.location();
        GeoViewport viewport = destination.viewport();
        GeoPoint low = viewport == null ? null : viewport.low();
        GeoPoint high = viewport == null ? null : viewport.high();

        TripEntity trip = tripRepository.save(TripEntity.create(
                request.title().trim(),
                destination.displayName(),
                destination.placeId(),
                destination.formattedAddress(),
                location == null ? null : location.latitude(),
                location == null ? null : location.longitude(),
                low == null ? null : low.latitude(),
                low == null ? null : low.longitude(),
                high == null ? null : high.latitude(),
                high == null ? null : high.longitude(),
                destination.types(),
                destination.primaryType(),
                request.startDate(),
                request.endDate(),
                owner,
                now
        ));
        tripMemberRepository.save(TripMemberEntity.owner(trip, owner, now));
        tripPlanningProfileRepository.save(TripPlanningProfileEntity.create(
                trip,
                request,
                accommodation,
                mustVisitPlaces,
                schedulePreference,
                now
        ));
        return trip;
    }

}
