package com.planmate.chat.service;

import com.planmate.chat.api.event.ChatMessageSentEvent;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.exception.ChatErrorCode;
import com.planmate.chat.exception.ChatException;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.api.TripAccessChecker;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {

    private static final int MAX_BODY_LENGTH = 2000;

    private final TripAccessChecker tripAccessChecker;
    private final ChatMessageRepository chatMessageRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ChatMessageService(
            TripAccessChecker tripAccessChecker,
            ChatMessageRepository chatMessageRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.chatMessageRepository = chatMessageRepository;
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
    public Result send(Long userId, Long tripId, String clientMessageId, String rawBody) {
        tripAccessChecker.checkAccessible(userId, tripId);

        ChatMessageEntity existing = chatMessageRepository.findByTripIdAndClientMessageId(tripId, clientMessageId)
                .orElse(null);
        if (existing != null) {
            return new Result(existing, false);
        }

        String body = validateBody(rawBody);
        ChatMessageEntity message = ChatMessageEntity.userText(tripId, userId, body, clientMessageId, Instant.now(clock));
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
        eventPublisher.publishEvent(toEvent(saved));
        return new Result(saved, true);
    }

    @Transactional(readOnly = true)
    public ChatMessageEntity findByClientMessageId(Long userId, Long tripId, String clientMessageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return chatMessageRepository.findByTripIdAndClientMessageId(tripId, clientMessageId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
    }

    private ChatMessageSentEvent toEvent(ChatMessageEntity message) {
        return new ChatMessageSentEvent(
                message.getTripId(),
                message.getId(),
                message.getClientMessageId(),
                message.getAuthorUserId(),
                message.getType(),
                message.getBody(),
                message.getSentAt()
        );
    }

    private String validateBody(String rawBody) {
        String trimmed = rawBody == null ? "" : rawBody.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_BODY_LENGTH) {
            throw new ChatException(ChatErrorCode.INVALID_MESSAGE_BODY);
        }
        return trimmed;
    }
}
