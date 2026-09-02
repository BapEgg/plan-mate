package com.planmate.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.planmate.chat.api.event.ChatTypingChangedEvent;
import com.planmate.chat.dto.ChatTypingState;
import com.planmate.trip.api.TripAccessChecker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class ChatTypingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Mock TripAccessChecker tripAccessChecker;
    @Mock TaskScheduler taskScheduler;
    @Mock ApplicationEventPublisher eventPublisher;
    private ChatTypingService service;

    @BeforeEach
    void setUp() {
        service = new ChatTypingService(
                tripAccessChecker,
                taskScheduler,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void startedPublishesOnlyMemberActivityWithSixSecondExpiry() {
        service.set(2L, 10L, ChatTypingState.STARTED, "device-a", "event-a");

        verify(tripAccessChecker).checkAccessible(2L, 10L);
        ArgumentCaptor<ChatTypingChangedEvent> event = ArgumentCaptor.forClass(ChatTypingChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().tripId()).isEqualTo(10L);
        assertThat(event.getValue().memberId()).isEqualTo(2L);
        assertThat(event.getValue().active()).isTrue();
        assertThat(event.getValue().expiresAtUtc()).isEqualTo(NOW.plusSeconds(6));
    }
}
