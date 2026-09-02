package com.planmate.chat.service;

import com.planmate.chat.api.event.ChatTypingChangedEvent;
import com.planmate.chat.dto.ChatTypingState;
import com.planmate.trip.api.TripAccessChecker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatTypingService {

    private static final Duration EXPIRY = Duration.ofSeconds(6);
    private static final int MAX_ID_LENGTH = 100;

    private final TripAccessChecker tripAccessChecker;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Map<Key, Entry> entries = new HashMap<>();
    private final Map<Long, AtomicLong> sequences = new HashMap<>();

    public ChatTypingService(
            TripAccessChecker tripAccessChecker,
            @Qualifier("realtimeTaskScheduler") TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public synchronized void set(
            Long userId,
            Long tripId,
            ChatTypingState state,
            String clientSessionId,
            String clientEventId
    ) {
        tripAccessChecker.checkAccessible(userId, tripId);
        if (state == null || !validId(clientSessionId) || !validId(clientEventId)) {
            return;
        }
        Key key = new Key(tripId, userId, clientSessionId);
        Entry previous = entries.get(key);
        if (previous != null && previous.clientEventId().equals(clientEventId)) {
            return;
        }
        if (state == ChatTypingState.STOPPED) {
            entries.remove(key);
            publishAggregate(tripId, userId);
            return;
        }

        Instant expiresAt = Instant.now(clock).plus(EXPIRY);
        entries.put(key, new Entry(expiresAt, clientEventId));
        taskScheduler.schedule(() -> expire(key, expiresAt), expiresAt);
        publishAggregate(tripId, userId);
    }

    private synchronized void expire(Key key, Instant expectedExpiry) {
        Entry entry = entries.get(key);
        if (entry == null || !entry.expiresAt().equals(expectedExpiry) || Instant.now(clock).isBefore(expectedExpiry)) {
            return;
        }
        entries.remove(key);
        publishAggregate(key.tripId(), key.userId());
    }

    private void publishAggregate(Long tripId, Long userId) {
        Instant expiresAt = entries.entrySet().stream()
                .filter(entry -> entry.getKey().tripId().equals(tripId) && entry.getKey().userId().equals(userId))
                .map(entry -> entry.getValue().expiresAt())
                .max(Comparator.naturalOrder())
                .orElse(null);
        long sequence = sequences.computeIfAbsent(tripId, ignored -> new AtomicLong()).incrementAndGet();
        eventPublisher.publishEvent(new ChatTypingChangedEvent(
                tripId, userId, expiresAt != null, expiresAt, sequence
        ));
    }

    private boolean validId(String value) {
        return StringUtils.hasText(value) && value.length() <= MAX_ID_LENGTH;
    }

    private record Key(Long tripId, Long userId, String clientSessionId) {
    }

    private record Entry(Instant expiresAt, String clientEventId) {
    }
}
