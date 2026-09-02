package com.planmate.trip.service;

import com.planmate.common.exception.CommonErrorCode;
import com.planmate.common.exception.CommonException;
import com.planmate.trip.api.TripActiveMemberReader;
import com.planmate.trip.api.TripMembershipChecker;
import com.planmate.trip.api.TripMembershipIntervalReader;
import com.planmate.trip.api.TripRoleChecker;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripMemberRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripMembershipQueryService implements TripMembershipChecker, TripRoleChecker,
        TripMembershipIntervalReader, TripActiveMemberReader {

    private final TripMemberRepository tripMemberRepository;

    public TripMembershipQueryService(TripMemberRepository tripMemberRepository) {
        this.tripMemberRepository = tripMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(Long userId, Long tripId) {
        return tripMemberRepository.existsByTrip_IdAndUser_IdAndStatus(tripId, userId, MembershipStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOwner(Long userId, Long tripId) {
        TripMemberEntity member = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(tripId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(TripNotFoundException::new);
        if (member.getRole() != TripMemberRole.OWNER) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Instant currentIntervalStartedAt(Long userId, Long tripId) {
        return activeMember(userId, tripId).getJoinedAt();
    }

    @Override
    @Transactional(readOnly = true)
    public Long currentLastReadChatMessageId(Long userId, Long tripId) {
        return activeMember(userId, tripId).getLastReadChatMessageId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> activeMemberIds(Long tripId) {
        return tripMemberRepository
                .findByTrip_IdAndStatusOrderByCreatedAtAsc(tripId, MembershipStatus.ACTIVE)
                .stream()
                .map(member -> member.getUser().getId())
                .toList();
    }

    private TripMemberEntity activeMember(Long userId, Long tripId) {
        return tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(tripId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(TripNotFoundException::new);
    }
}
