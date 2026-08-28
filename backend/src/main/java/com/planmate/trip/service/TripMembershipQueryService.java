package com.planmate.trip.service;

import com.planmate.trip.api.TripMembershipChecker;
import com.planmate.trip.repository.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripMembershipQueryService implements TripMembershipChecker {

    private final TripMemberRepository tripMemberRepository;

    public TripMembershipQueryService(TripMemberRepository tripMemberRepository) {
        this.tripMemberRepository = tripMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(Long userId, Long tripId) {
        return tripMemberRepository.existsByTrip_IdAndUser_Id(tripId, userId);
    }
}
