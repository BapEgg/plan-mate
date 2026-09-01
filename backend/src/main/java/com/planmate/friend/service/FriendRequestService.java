package com.planmate.friend.service;

import com.planmate.auth.service.AuthNormalizer;
import com.planmate.common.exception.CommonErrorCode;
import com.planmate.common.exception.CommonException;
import com.planmate.friend.dto.FriendResponse;
import com.planmate.friend.entity.FriendRequestEntity;
import com.planmate.friend.entity.FriendRequestStatus;
import com.planmate.friend.exception.FriendErrorCode;
import com.planmate.friend.exception.FriendException;
import com.planmate.friend.repository.FriendRequestRepository;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-B: 친구 관계는 여행방 멤버십과 독립적으로 수락한다(spec §5.1). "친구다"는 상태는 ACCEPTED
 * 행의 존재 자체로 표현하며 별도 friendship table을 두지 않는다.
 */
@Service
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final AuthNormalizer authNormalizer;
    private final Clock clock;

    public FriendRequestService(
            FriendRequestRepository friendRequestRepository,
            UserRepository userRepository,
            AuthNormalizer authNormalizer,
            Clock clock
    ) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
        this.authNormalizer = authNormalizer;
        this.clock = clock;
    }

    @Transactional
    public FriendRequestEntity send(Long requesterId, Long addresseeUserId, String addresseeEmail) {
        UserEntity addressee = resolveAddressee(addresseeUserId, addresseeEmail);
        if (addressee.getId().equals(requesterId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN, "본인에게 친구 요청을 보낼 수 없습니다.");
        }
        if (friendRequestRepository.areFriends(requesterId, addressee.getId())) {
            throw new FriendException(FriendErrorCode.ALREADY_FRIENDS);
        }
        friendRequestRepository.findPendingBetween(requesterId, addressee.getId())
                .ifPresent(existing -> {
                    throw new FriendException(FriendErrorCode.DUPLICATE_PENDING_FRIEND_REQUEST);
                });
        try {
            return friendRequestRepository.save(
                    FriendRequestEntity.create(requesterId, addressee.getId(), Instant.now(clock))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new FriendException(FriendErrorCode.DUPLICATE_PENDING_FRIEND_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public List<FriendRequestEntity> listIncoming(Long userId) {
        return friendRequestRepository.findByAddresseeUserIdAndStatusOrderByCreatedAtDesc(userId, FriendRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestEntity> listOutgoing(Long userId) {
        return friendRequestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(userId, FriendRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(Long userId) {
        return friendRequestRepository.findAcceptedInvolving(userId).stream()
                .map(request -> request.getRequesterUserId().equals(userId) ? request.getAddresseeUserId() : request.getRequesterUserId())
                .map(friendUserId -> userRepository.findById(friendUserId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(user -> new FriendResponse(user.getId(), user.getNickname(), user.getProfileImageUrl()))
                .toList();
    }

    @Transactional
    public void accept(Long userId, Long requestId) {
        FriendRequestEntity request = requirePendingRequest(requestId);
        if (!request.getAddresseeUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        request.accept(Instant.now(clock));
    }

    @Transactional
    public void decline(Long userId, Long requestId) {
        FriendRequestEntity request = requirePendingRequest(requestId);
        if (!request.getAddresseeUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        request.decline(Instant.now(clock));
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        FriendRequestEntity request = requirePendingRequest(requestId);
        if (!request.getRequesterUserId().equals(userId)) {
            throw new CommonException(CommonErrorCode.FORBIDDEN);
        }
        request.cancel(Instant.now(clock));
    }

    private UserEntity resolveAddressee(Long addresseeUserId, String addresseeEmail) {
        if (addresseeUserId != null) {
            return userRepository.findById(addresseeUserId)
                    .orElseThrow(() -> new FriendException(FriendErrorCode.ADDRESSEE_NOT_FOUND));
        }
        if (addresseeEmail != null && !addresseeEmail.isBlank()) {
            String normalized = authNormalizer.normalizeEmail(addresseeEmail);
            return userRepository.findByEmailCanonical(normalized)
                    .orElseThrow(() -> new FriendException(FriendErrorCode.ADDRESSEE_NOT_FOUND));
        }
        throw new FriendException(FriendErrorCode.ADDRESSEE_NOT_FOUND, "친구를 요청할 계정 또는 email을 입력해 주세요.");
    }

    private FriendRequestEntity requirePendingRequest(Long requestId) {
        FriendRequestEntity request = friendRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new FriendException(FriendErrorCode.FRIEND_REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new FriendException(FriendErrorCode.FRIEND_REQUEST_ALREADY_RESOLVED);
        }
        return request;
    }
}
