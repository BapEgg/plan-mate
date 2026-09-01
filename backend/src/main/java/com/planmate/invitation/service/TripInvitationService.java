package com.planmate.invitation.service;

import com.planmate.auth.service.AuthNormalizer;
import com.planmate.common.exception.CommonErrorCode;
import com.planmate.common.exception.CommonException;
import com.planmate.common.realtime.RealtimeEventType;
import com.planmate.invitation.entity.InvitationStatus;
import com.planmate.invitation.entity.TripInvitationEntity;
import com.planmate.invitation.exception.InvitationErrorCode;
import com.planmate.invitation.exception.InvitationException;
import com.planmate.invitation.repository.TripInvitationRepository;
import com.planmate.membership.api.event.MembershipChangeType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import com.planmate.realtime.PrivateRealtimeEventPublisher;
import com.planmate.trip.api.TripRoleChecker;
import com.planmate.trip.entity.MembershipStatus;
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
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-B: 내부 여행 초대. 협업 계정은 OWNER 포함 최대 20명이며 ACTIVE + 유효 PENDING 초대가 자리를
 * 사용한다(spec §5.1).
 */
@Service
public class TripInvitationService {

    public static final int MAX_TRIP_MEMBERS = 20;

    private final TripInvitationRepository tripInvitationRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripRoleChecker tripRoleChecker;
    private final AuthNormalizer authNormalizer;
    private final PrivateRealtimeEventPublisher privateRealtimeEventPublisher;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public TripInvitationService(
            TripInvitationRepository tripInvitationRepository,
            TripMemberRepository tripMemberRepository,
            TripRepository tripRepository,
            UserRepository userRepository,
            TripRoleChecker tripRoleChecker,
            AuthNormalizer authNormalizer,
            PrivateRealtimeEventPublisher privateRealtimeEventPublisher,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripInvitationRepository = tripInvitationRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.tripRoleChecker = tripRoleChecker;
        this.authNormalizer = authNormalizer;
        this.privateRealtimeEventPublisher = privateRealtimeEventPublisher;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TripInvitationEntity send(Long ownerId, Long tripId, Long inviteeUserId, String inviteeEmail) {
        tripRoleChecker.requireOwner(ownerId, tripId);
        UserEntity invitee = resolveInvitee(inviteeUserId, inviteeEmail);
        if (invitee.getId().equals(ownerId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN, "본인은 초대할 수 없습니다.");
        }
        if (tripMemberRepository.existsByTrip_IdAndUser_IdAndStatus(tripId, invitee.getId(), MembershipStatus.ACTIVE)) {
            throw new InvitationException(InvitationErrorCode.INVITEE_ALREADY_ACTIVE_MEMBER);
        }
        // 여러 초대 command가 같은 trip 정원을 동시에 검사하지 않도록 trip row를 잠근다.
        TripEntity trip = tripRepository.findByIdForUpdate(tripId).orElseThrow(TripNotFoundException::new);
        Instant now = Instant.now(clock);
        tripInvitationRepository.findByTripIdAndInviteeUserIdAndStatus(tripId, invitee.getId(), InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    if (!existing.isExpired(now)) {
                        throw new InvitationException(InvitationErrorCode.DUPLICATE_PENDING_INVITATION);
                    }
                });
        long activeMembers = tripMemberRepository.countByTrip_IdAndStatus(tripId, MembershipStatus.ACTIVE);
        long pendingInvites = tripInvitationRepository.countActivePendingByTripId(tripId, now);
        if (activeMembers + pendingInvites >= MAX_TRIP_MEMBERS) {
            throw new InvitationException(InvitationErrorCode.TRIP_MEMBER_CAPACITY_EXCEEDED);
        }

        TripInvitationEntity invitation;
        try {
            invitation = tripInvitationRepository.save(
                    TripInvitationEntity.create(trip.getId(), invitee.getId(), ownerId, now)
            );
        } catch (DataIntegrityViolationException exception) {
            throw new InvitationException(InvitationErrorCode.DUPLICATE_PENDING_INVITATION);
        }

        privateRealtimeEventPublisher.sendToUser(
                invitee.getId(), tripId, RealtimeEventType.INVITATION_RECEIVED, TripInvitationSummary.from(invitation)
        );
        return invitation;
    }

    @Transactional(readOnly = true)
    public List<TripInvitationEntity> listMine(Long userId) {
        return tripInvitationRepository.findByInviteeUserIdAndStatusOrderByCreatedAtDesc(userId, InvitationStatus.PENDING);
    }

    @Transactional
    public void accept(Long userId, Long invitationId) {
        TripInvitationEntity invitation = requirePendingInvitation(invitationId);
        if (!invitation.getInviteeUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        Instant now = Instant.now(clock);
        TripEntity trip = tripRepository.findByIdForUpdate(invitation.getTripId()).orElseThrow(TripNotFoundException::new);
        if (tripMemberRepository.existsByTrip_IdAndUser_IdAndStatus(trip.getId(), userId, MembershipStatus.ACTIVE)) {
            invitation.accept(now);
            return;
        }
        UserEntity invitee = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        tripMemberRepository.save(TripMemberEntity.member(trip, invitee, now));
        invitation.accept(now);

        eventPublisher.publishEvent(new TripMembershipChangedEvent(trip.getId(), userId, MembershipChangeType.JOINED));
    }

    @Transactional
    public void decline(Long userId, Long invitationId) {
        TripInvitationEntity invitation = requirePendingInvitation(invitationId);
        if (!invitation.getInviteeUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        invitation.decline(Instant.now(clock));
    }

    @Transactional
    public void cancel(Long ownerId, Long invitationId) {
        TripInvitationEntity invitation = requirePendingInvitation(invitationId);
        tripRoleChecker.requireOwner(ownerId, invitation.getTripId());
        invitation.cancel(Instant.now(clock));
    }

    private UserEntity resolveInvitee(Long inviteeUserId, String inviteeEmail) {
        if (inviteeUserId != null) {
            return userRepository.findById(inviteeUserId)
                    .orElseThrow(() -> new InvitationException(InvitationErrorCode.INVITEE_NOT_FOUND));
        }
        if (inviteeEmail != null && !inviteeEmail.isBlank()) {
            String normalized = authNormalizer.normalizeEmail(inviteeEmail);
            return userRepository.findByEmailCanonical(normalized)
                    .orElseThrow(() -> new InvitationException(InvitationErrorCode.INVITEE_NOT_FOUND));
        }
        throw new InvitationException(InvitationErrorCode.INVITEE_NOT_FOUND, "초대할 친구 또는 email을 입력해 주세요.");
    }

    private TripInvitationEntity requirePendingInvitation(Long invitationId) {
        TripInvitationEntity invitation = tripInvitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new InvitationException(InvitationErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.isPending()) {
            throw new InvitationException(InvitationErrorCode.INVITATION_ALREADY_RESOLVED);
        }
        Instant now = Instant.now(clock);
        if (invitation.isExpired(now)) {
            invitation.markExpired(now);
            throw new InvitationException(InvitationErrorCode.INVITATION_EXPIRED);
        }
        return invitation;
    }
}
