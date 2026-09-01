package com.planmate.chat.service;

import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.exception.ChatErrorCode;
import com.planmate.chat.exception.ChatException;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripMembershipChatReadTracker;
import com.planmate.trip.api.TripMembershipIntervalReader;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WP-D phase 3: spec §4 "읽음과 visibility". */
@Service
public class ChatUnreadService {

    private final TripAccessChecker tripAccessChecker;
    private final TripMembershipIntervalReader intervalReader;
    private final TripMembershipChatReadTracker readTracker;
    private final ChatMessageRepository chatMessageRepository;

    public ChatUnreadService(
            TripAccessChecker tripAccessChecker,
            TripMembershipIntervalReader intervalReader,
            TripMembershipChatReadTracker readTracker,
            ChatMessageRepository chatMessageRepository
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.intervalReader = intervalReader;
        this.readTracker = readTracker;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Instant intervalStart = intervalReader.currentIntervalStartedAt(userId, tripId);
        Long lastReadId = intervalReader.currentLastReadChatMessageId(userId, tripId);
        return chatMessageRepository.countUnread(tripId, userId, intervalStart, lastReadId);
    }

    @Transactional
    public void markRead(Long userId, Long tripId, Long messageId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ChatMessageEntity message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getTripId().equals(tripId)) {
            throw new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }
        readTracker.markChatRead(userId, tripId, messageId);
    }
}
