package com.planmate.chat.service;

import com.planmate.chat.api.event.ChatReactionChangedEvent;
import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.entity.ChatMessageReactionEntity;
import com.planmate.chat.entity.ChatReactionType;
import com.planmate.chat.exception.ChatErrorCode;
import com.planmate.chat.exception.ChatException;
import com.planmate.chat.repository.ChatMessageReactionRepository;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatReactionService {

    private final TripAccessChecker tripAccessChecker;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ChatReactionService(
            TripAccessChecker tripAccessChecker,
            ChatMessageRepository messageRepository,
            ChatMessageReactionRepository reactionRepository,
            UserRepository userRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ChatMessageEntity set(Long userId, Long tripId, Long messageId, ChatReactionType type) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ChatMessageEntity message = availableMessage(tripId, messageId);
        if (type == null) {
            throw new ChatException(ChatErrorCode.INVALID_REACTION);
        }

        ChatMessageReactionEntity existing = reactionRepository
                .findByMessage_IdAndUser_Id(messageId, userId)
                .orElse(null);
        if (existing != null && existing.getType() == type) {
            return message;
        }
        if (existing != null) {
            existing.changeType(type);
        } else {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new ChatException(ChatErrorCode.INVALID_REACTION));
            reactionRepository.save(ChatMessageReactionEntity.create(message, user, type, Instant.now(clock)));
        }
        eventPublisher.publishEvent(new ChatReactionChangedEvent(tripId, messageId));
        return message;
    }

    @Transactional
    public ChatMessageEntity remove(Long userId, Long tripId, Long messageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ChatMessageEntity message = availableMessage(tripId, messageId);
        ChatMessageReactionEntity existing = reactionRepository
                .findByMessage_IdAndUser_Id(messageId, userId)
                .orElse(null);
        if (existing != null) {
            reactionRepository.delete(existing);
            eventPublisher.publishEvent(new ChatReactionChangedEvent(tripId, messageId));
        }
        return message;
    }

    private ChatMessageEntity availableMessage(Long tripId, Long messageId) {
        ChatMessageEntity message = messageRepository.findByIdAndTripId(messageId, tripId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (message.isDeleted()) {
            throw new ChatException(ChatErrorCode.MESSAGE_ALREADY_DELETED);
        }
        return message;
    }
}
