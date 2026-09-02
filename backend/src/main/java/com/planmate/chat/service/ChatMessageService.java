package com.planmate.chat.service;

import com.planmate.chat.api.event.ChatMessageSentEvent;
import com.planmate.chat.dto.ChatMentionRequest;
import com.planmate.chat.dto.ChatMentionResponse;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.entity.ChatMessageMentionEntity;
import com.planmate.chat.exception.ChatErrorCode;
import com.planmate.chat.exception.ChatException;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.chat.repository.ChatMessageMentionRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripMembershipIntervalReader;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.repository.TripMemberRepository;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {

    private static final int MAX_BODY_LENGTH = 2000;

    private final TripAccessChecker tripAccessChecker;
    private final TripMembershipIntervalReader intervalReader;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMentionRepository mentionRepository;
    private final TripMemberRepository tripMemberRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ChatMessageService(
            TripAccessChecker tripAccessChecker,
            TripMembershipIntervalReader intervalReader,
            ChatMessageRepository chatMessageRepository,
            ChatMessageMentionRepository mentionRepository,
            TripMemberRepository tripMemberRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.intervalReader = intervalReader;
        this.chatMessageRepository = chatMessageRepository;
        this.mentionRepository = mentionRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    public record Result(ChatMessageEntity message, boolean created) {
    }

    /**
     * A resend of the same (tripId, clientMessageId) returns the existing row without inserting
     * again or re-publishing the realtime event — spec's "DUPLICATE_CLIENT_MESSAGE_ID: 200/idempotent
     * replay, not an exception". {@code Result.created} tells the controller whether to answer
     * 201 (genuine send) or 200 (replay).
     */
    @Transactional
    public Result send(
            Long userId,
            Long tripId,
            String clientMessageId,
            String rawBody,
            Long replyToMessageId,
            List<ChatMentionRequest> mentionRequests
    ) {
        tripAccessChecker.checkAccessible(userId, tripId);

        ChatMessageEntity existing = chatMessageRepository.findByTripIdAndClientMessageId(tripId, clientMessageId)
                .orElse(null);
        if (existing != null) {
            return new Result(existing, false);
        }

        String body = validateBody(rawBody);
        ChatMessageEntity replyTarget = resolveReplyTarget(userId, tripId, replyToMessageId);
        ChatMessageEntity message = ChatMessageEntity.userText(
                tripId,
                userId,
                body,
                clientMessageId,
                Instant.now(clock),
                replyTarget == null ? null : replyTarget.getId()
        );
        ChatMessageEntity saved;
        try {
            saved = chatMessageRepository.save(message);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent resend of the same clientMessageId raced us to the unique index —
            // still an idempotent replay, not an error, so fall back to the row it created.
            return new Result(
                    chatMessageRepository.findByTripIdAndClientMessageId(tripId, clientMessageId)
                            .orElseThrow(() -> exception),
                    false
            );
        }
        List<ChatMessageMentionEntity> mentions = validMentions(saved, userId, tripId, body, mentionRequests);
        if (!mentions.isEmpty()) {
            mentionRepository.saveAll(mentions);
        }
        eventPublisher.publishEvent(toEvent(saved, replyTarget, mentions));
        return new Result(saved, true);
    }

    @Transactional(readOnly = true)
    public ChatMessageEntity findByClientMessageId(Long userId, Long tripId, String clientMessageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return chatMessageRepository.findByTripIdAndClientMessageId(tripId, clientMessageId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ChatMessageEntity findById(Long userId, Long tripId, Long messageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return findMessage(tripId, messageId);
    }

    @Transactional
    public ChatMessageEntity delete(Long userId, Long tripId, Long messageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ChatMessageEntity message = findMessage(tripId, messageId);
        if (!userId.equals(message.getAuthorUserId())) {
            throw new ChatException(ChatErrorCode.MESSAGE_DELETE_FORBIDDEN);
        }
        if (message.isDeleted()) {
            return message;
        }

        Instant now = Instant.now(clock);
        if (now.isAfter(message.getSentAt().plus(Duration.ofMinutes(5)))) {
            throw new ChatException(ChatErrorCode.MESSAGE_DELETE_WINDOW_EXPIRED);
        }
        message.delete(now);
        mentionRepository.deleteByMessage_Id(messageId);
        eventPublisher.publishEvent(new com.planmate.chat.api.event.ChatMessageDeletedEvent(
                tripId,
                messageId,
                now
        ));
        return message;
    }

    private ChatMessageSentEvent toEvent(
            ChatMessageEntity message,
            ChatMessageEntity replyTarget,
            List<ChatMessageMentionEntity> mentions
    ) {
        return new ChatMessageSentEvent(
                message.getTripId(),
                message.getId(),
                message.getClientMessageId(),
                message.getAuthorUserId(),
                message.getType(),
                message.getBody(),
                message.getSentAt(),
                replyTarget == null ? null : replyTarget.getId(),
                replyTarget == null ? null : replyTarget.getAuthorUserId(),
                replyTarget == null ? null : replyTarget.isDeleted() ? "삭제된 메시지입니다." : compact(replyTarget.getBody()),
                replyTarget != null && replyTarget.isDeleted(),
                mentions.stream().map(this::toMentionResponse).toList()
        );
    }

    private List<ChatMessageMentionEntity> validMentions(
            ChatMessageEntity message,
            Long authorUserId,
            Long tripId,
            String body,
            List<ChatMentionRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<TripMemberEntity> members = tripMemberRepository
                .findByTrip_IdAndStatusOrderByCreatedAtAsc(tripId, MembershipStatus.ACTIVE);
        List<ChatMessageMentionEntity> valid = new ArrayList<>();
        Set<String> usedRanges = new HashSet<>();
        int bodyLength = body.codePointCount(0, body.length());
        for (ChatMentionRequest request : requests) {
            if (request == null || request.memberId() == null || request.memberId().equals(authorUserId)
                    || request.startCodePoint() == null || request.endCodePoint() == null) {
                continue;
            }
            int start = request.startCodePoint();
            int end = request.endCodePoint();
            if (start < 0 || end <= start || end > bodyLength || !usedRanges.add(start + ":" + end)) {
                continue;
            }
            TripMemberEntity target = members.stream()
                    .filter(member -> member.getUser().getId().equals(request.memberId()))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                continue;
            }
            String nickname = target.getUser().getNickname();
            int startOffset = body.offsetByCodePoints(0, start);
            int endOffset = body.offsetByCodePoints(0, end);
            if (!("@" + nickname).equals(body.substring(startOffset, endOffset))) {
                continue;
            }
            valid.add(ChatMessageMentionEntity.create(message, target.getUser(), nickname, start, end));
        }
        return valid;
    }

    private ChatMentionResponse toMentionResponse(ChatMessageMentionEntity mention) {
        return new ChatMentionResponse(
                mention.getMentionedUser().getId(),
                mention.getDisplayNameSnapshot(),
                mention.getStartCodePoint(),
                mention.getEndCodePoint()
        );
    }

    private ChatMessageEntity resolveReplyTarget(Long userId, Long tripId, Long replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        ChatMessageEntity target = chatMessageRepository.findByIdAndTripId(replyToMessageId, tripId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.INVALID_REPLY_TARGET));
        Instant intervalStart = intervalReader.currentIntervalStartedAt(userId, tripId);
        if (!target.getSentAt().isAfter(intervalStart)) {
            throw new ChatException(ChatErrorCode.INVALID_REPLY_TARGET);
        }
        if (target.isDeleted()) {
            throw new ChatException(ChatErrorCode.MESSAGE_ALREADY_DELETED);
        }
        return target;
    }

    private ChatMessageEntity findMessage(Long tripId, Long messageId) {
        return chatMessageRepository.findByIdAndTripId(messageId, tripId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
    }

    private String compact(String body) {
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 79) + "…";
    }

    private String validateBody(String rawBody) {
        String trimmed = Normalizer.normalize(rawBody == null ? "" : rawBody.trim(), Normalizer.Form.NFC);
        if (trimmed.isEmpty() || trimmed.length() > MAX_BODY_LENGTH) {
            throw new ChatException(ChatErrorCode.INVALID_MESSAGE_BODY);
        }
        return trimmed;
    }
}
