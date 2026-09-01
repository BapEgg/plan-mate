package com.planmate.chat.service;

import com.planmate.chat.entity.ChatMessageEntity;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripMembershipIntervalReader;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final TripAccessChecker tripAccessChecker;
    private final TripMembershipIntervalReader intervalReader;
    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(
            TripAccessChecker tripAccessChecker,
            TripMembershipIntervalReader intervalReader,
            ChatMessageRepository chatMessageRepository
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.intervalReader = intervalReader;
        this.chatMessageRepository = chatMessageRepository;
    }

    public record Page(List<ChatMessageEntity> messages, Long nextCursor) {
    }

    /**
     * Keyset pagination, newest first: {@code cursor} is the last message id the caller already
     * has, so the next page is strictly older. {@code nextCursor} is null once there's nothing
     * older left to fetch. Scoped to the caller's current membership interval (spec §4: a rejoined
     * member never sees messages from before they left).
     */
    @Transactional(readOnly = true)
    public Page listHistory(Long userId, Long tripId, Long cursor, Integer size) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Instant intervalStart = intervalReader.currentIntervalStartedAt(userId, tripId);
        int pageSize = normalizeSize(size);
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<ChatMessageEntity> fetched = cursor == null
                ? chatMessageRepository.findByTripIdAndSentAtAfterOrderByIdDesc(tripId, intervalStart, pageRequest)
                : chatMessageRepository.findByTripIdAndIdLessThanAndSentAtAfterOrderByIdDesc(tripId, cursor, intervalStart, pageRequest);

        boolean hasMore = fetched.size() > pageSize;
        List<ChatMessageEntity> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new Page(page, nextCursor);
    }

    /**
     * Gap recovery after a reconnect: everything strictly newer than {@code sinceId}, ascending,
     * capped at {@link #MAX_PAGE_SIZE} — a gap that large means something else needs attention,
     * not an unbounded query. Same interval scoping as {@link #listHistory}.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageEntity> listSince(Long userId, Long tripId, Long sinceId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Instant intervalStart = intervalReader.currentIntervalStartedAt(userId, tripId);
        return chatMessageRepository.findByTripIdAndIdGreaterThanAndSentAtAfterOrderByIdAsc(
                tripId, sinceId, intervalStart, PageRequest.of(0, MAX_PAGE_SIZE)
        );
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
