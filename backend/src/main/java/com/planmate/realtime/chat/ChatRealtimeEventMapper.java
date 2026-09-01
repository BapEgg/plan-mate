package com.planmate.realtime.chat;

import com.planmate.chat.api.event.ChatMessageSentEvent;
import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ChatRealtimeEventMapper {

    private final Clock clock;

    public ChatRealtimeEventMapper(Clock clock) {
        this.clock = clock;
    }

    public RealtimeEventEnvelope<ChatMessageSentPayload> toEnvelope(ChatMessageSentEvent event) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.CHAT_MESSAGE_SENT,
                event.tripId(),
                Instant.now(clock),
                new ChatMessageSentPayload(
                        event.messageId(),
                        event.clientMessageId(),
                        event.authorUserId(),
                        event.type(),
                        event.body(),
                        event.sentAt()
                )
        );
    }
}
