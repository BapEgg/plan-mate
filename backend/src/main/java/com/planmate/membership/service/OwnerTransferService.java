package com.planmate.membership.service;

import com.planmate.common.exception.CommonErrorCode;
import com.planmate.common.exception.CommonException;
import com.planmate.membership.api.event.MembershipChangeType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import com.planmate.membership.entity.OwnerTransferRequestEntity;
import com.planmate.membership.entity.OwnerTransferRequestStatus;
import com.planmate.membership.exception.MembershipErrorCode;
import com.planmate.membership.exception.MembershipException;
import com.planmate.membership.repository.OwnerTransferRequestRepository;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.repository.TripMemberRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-B: 방장 이전. 대상 MEMBER가 수락한 순간에만 role이 원자적으로 교체된다(spec §5.1).
 */
@Service
public class OwnerTransferService {

    private final OwnerTransferRequestRepository ownerTransferRequestRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripMembershipCommandService membershipCommandService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public OwnerTransferService(
            OwnerTransferRequestRepository ownerTransferRequestRepository,
            TripMemberRepository tripMemberRepository,
            TripMembershipCommandService membershipCommandService,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ownerTransferRequestRepository = ownerTransferRequestRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.membershipCommandService = membershipCommandService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OwnerTransferRequestEntity create(Long ownerId, Long tripId, Long targetUserId) {
        membershipCommandService.requireActiveMember(ownerId, tripId, TripMemberRole.OWNER);
        if (targetUserId.equals(ownerId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN, "본인에게 방장을 이전할 수 없습니다.");
        }
        TripMemberEntity target = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(tripId, targetUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new MembershipException(MembershipErrorCode.TARGET_NOT_ACTIVE_MEMBER));
        if (target.getRole() != TripMemberRole.MEMBER) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        ownerTransferRequestRepository.findByTripIdAndStatus(tripId, OwnerTransferRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new MembershipException(MembershipErrorCode.DUPLICATE_OWNER_TRANSFER_REQUEST);
                });
        try {
            return ownerTransferRequestRepository.save(
                    OwnerTransferRequestEntity.create(tripId, ownerId, targetUserId, Instant.now(clock))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new MembershipException(MembershipErrorCode.DUPLICATE_OWNER_TRANSFER_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<OwnerTransferRequestEntity> listIncoming(Long userId) {
        return ownerTransferRequestRepository.findByToUserIdAndStatusOrderByCreatedAtDesc(
                userId, OwnerTransferRequestStatus.PENDING
        );
    }

    @Transactional
    public void accept(Long userId, Long requestId) {
        OwnerTransferRequestEntity request = requirePendingRequest(requestId);
        if (!request.getToUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        Instant now = Instant.now(clock);
        TripMemberEntity fromMember = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(request.getTripId(), request.getFromUserId(), MembershipStatus.ACTIVE)
                .filter(member -> member.getRole() == TripMemberRole.OWNER)
                .orElseThrow(() -> new MembershipException(MembershipErrorCode.TARGET_NOT_ACTIVE_MEMBER));
        TripMemberEntity toMember = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(request.getTripId(), request.getToUserId(), MembershipStatus.ACTIVE)
                .filter(member -> member.getRole() == TripMemberRole.MEMBER)
                .orElseThrow(() -> new MembershipException(MembershipErrorCode.TARGET_NOT_ACTIVE_MEMBER));

        fromMember.changeRole(TripMemberRole.MEMBER);
        toMember.changeRole(TripMemberRole.OWNER);
        request.accept(now);

        eventPublisher.publishEvent(
                new TripMembershipChangedEvent(request.getTripId(), userId, MembershipChangeType.OWNER_TRANSFERRED)
        );
    }

    @Transactional
    public void decline(Long userId, Long requestId) {
        OwnerTransferRequestEntity request = requirePendingRequest(requestId);
        if (!request.getToUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        request.decline(Instant.now(clock));
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        OwnerTransferRequestEntity request = requirePendingRequest(requestId);
        if (!request.getFromUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        request.cancel(Instant.now(clock));
    }

    private OwnerTransferRequestEntity requirePendingRequest(Long requestId) {
        OwnerTransferRequestEntity request = ownerTransferRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new MembershipException(MembershipErrorCode.OWNER_TRANSFER_REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new MembershipException(MembershipErrorCode.OWNER_TRANSFER_REQUEST_ALREADY_RESOLVED);
        }
        if (request.isExpired(Instant.now(clock))) {
            request.markExpired(Instant.now(clock));
            throw new MembershipException(MembershipErrorCode.OWNER_TRANSFER_REQUEST_EXPIRED);
        }
        return request;
    }
}
