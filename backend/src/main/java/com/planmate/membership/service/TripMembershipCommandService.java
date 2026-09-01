package com.planmate.membership.service;

import com.planmate.common.exception.CommonErrorCode;
import com.planmate.common.exception.CommonException;
import com.planmate.membership.api.event.MembershipChangeType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import com.planmate.membership.exception.MembershipErrorCode;
import com.planmate.membership.exception.MembershipException;
import com.planmate.trip.api.TripMembershipChatReadTracker;
import com.planmate.trip.entity.LeftReason;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-B: 여행방 제목 수정, MEMBER 내보내기, 본인 나가기. 모든 command는 UI가 action을 숨기는 것과
 * 무관하게 서버가 현재 role을 다시 검사한다(spec §4.1).
 */
@Service
public class TripMembershipCommandService implements TripMembershipChatReadTracker {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public TripMembershipCommandService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void updateTitle(Long userId, Long tripId, String rawTitle) {
        requireActiveMember(userId, tripId, TripMemberRole.OWNER);
        String normalized = TripTitleValidator.normalizeAndValidate(rawTitle);
        TripEntity trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        trip.updateTitle(normalized, Instant.now(clock));
        eventPublisher.publishEvent(new TripMembershipChangedEvent(tripId, null, MembershipChangeType.TITLE_UPDATED));
    }

    @Transactional
    public void removeMember(Long ownerId, Long tripId, Long targetUserId) {
        requireActiveMember(ownerId, tripId, TripMemberRole.OWNER);
        if (targetUserId.equals(ownerId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN, "본인은 내보낼 수 없습니다.");
        }
        TripMemberEntity target = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(tripId, targetUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new MembershipException(MembershipErrorCode.TARGET_NOT_ACTIVE_MEMBER));
        if (target.getRole() != TripMemberRole.MEMBER) {
            throw new CommonException(CommonErrorCode.FORBIDDEN, "다른 OWNER는 내보낼 수 없습니다.");
        }
        target.end(LeftReason.REMOVED, Instant.now(clock));
        eventPublisher.publishEvent(new TripMembershipChangedEvent(tripId, targetUserId, MembershipChangeType.REMOVED));
    }

    @Transactional
    public void leave(Long userId, Long tripId) {
        TripMemberEntity member = requireActiveMember(userId, tripId, null);
        if (member.getRole() == TripMemberRole.OWNER) {
            throw new MembershipException(MembershipErrorCode.OWNER_CANNOT_LEAVE);
        }
        member.end(LeftReason.LEFT, Instant.now(clock));
        eventPublisher.publishEvent(new TripMembershipChangedEvent(tripId, userId, MembershipChangeType.LEFT));
    }

    @Override
    @Transactional
    public void markChatRead(Long userId, Long tripId, Long messageId) {
        TripMemberEntity member = requireActiveMember(userId, tripId, null);
        member.markChatRead(messageId);
    }

    /**
     * 접근 가능한 ACTIVE membership을 반환한다. {@code requiredRole}이 주어지면 role도 재검사한다.
     * 멤버가 아니면(비공개 여부를 노출하지 않기 위해) {@link TripNotFoundException}(404)을 던진다.
     */
    TripMemberEntity requireActiveMember(Long userId, Long tripId, TripMemberRole requiredRole) {
        TripMemberEntity member = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(tripId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(TripNotFoundException::new);
        if (requiredRole != null && member.getRole() != requiredRole) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        return member;
    }
}
