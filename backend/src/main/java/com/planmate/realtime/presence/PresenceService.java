package com.planmate.realtime.presence;

import com.planmate.realtime.RealtimeSessionRegistry;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripActiveMemberReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private static final Duration UNEXPECTED_DISCONNECT_GRACE = Duration.ofSeconds(10);

    private final RealtimeSessionRegistry sessionRegistry;
    private final TripAccessChecker tripAccessChecker;
    private final TripActiveMemberReader activeMemberReader;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PresenceService(
            RealtimeSessionRegistry sessionRegistry,
            TripAccessChecker tripAccessChecker,
            TripActiveMemberReader activeMemberReader,
            @Qualifier("realtimeTaskScheduler") TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.sessionRegistry = sessionRegistry;
        this.tripAccessChecker = tripAccessChecker;
        this.activeMemberReader = activeMemberReader;
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void register(String sessionId, Long userId, Long tripId) {
        publish(sessionRegistry.registerSubscription(sessionId, userId, tripId));
    }

    public void disconnectImmediately(String sessionId) {
        sessionRegistry.removeSession(sessionId).forEach(this::publish);
    }

    public void disconnectWithGrace(String sessionId) {
        if (!sessionRegistry.hasSession(sessionId)) {
            return;
        }
        try {
            taskScheduler.schedule(
                    () -> sessionRegistry.removeSession(sessionId).forEach(this::publish),
                    Instant.now(clock).plus(UNEXPECTED_DISCONNECT_GRACE)
            );
        } catch (TaskRejectedException ignored) {
            // The scheduler can stop before WebSocket sessions during application shutdown.
            // Clean up the in-memory registry without publishing into a closing context.
            sessionRegistry.removeSession(sessionId);
        }
    }

    public TripPresenceResponse snapshot(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        List<MemberPresenceResponse> members = activeMemberReader.activeMemberIds(tripId)
                .stream()
                .map(memberId -> new MemberPresenceResponse(
                        memberId,
                        sessionRegistry.isOnline(tripId, memberId)
                                ? PresenceStatus.ONLINE
                                : PresenceStatus.OFFLINE
                ))
                .toList();
        return new TripPresenceResponse(tripId, members, sessionRegistry.version(tripId));
    }

    private void publish(RealtimeSessionRegistry.PresenceTransition transition) {
        if (transition == null) {
            return;
        }
        eventPublisher.publishEvent(new MemberPresenceChangedEvent(
                transition.tripId(),
                transition.userId(),
                transition.online() ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE,
                Instant.now(clock),
                transition.version()
        ));
    }
}
