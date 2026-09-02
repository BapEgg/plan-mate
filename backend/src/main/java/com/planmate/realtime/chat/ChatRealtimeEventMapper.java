package com.planmate.realtime.chat;

import com.planmate.chat.api.event.ChatMessageSentEvent;
import com.planmate.chat.api.event.ChatMessageDeletedEvent;
import com.planmate.chat.api.event.ChatReactionChangedEvent;
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
                        event.sentAt(),
                        event.replyToMessageId(),
                        event.replyAuthorUserId(),
                        event.replyBody(),
                        event.replyDeleted(),
                        event.mentions()
                )
        );
    }

    public RealtimeEventEnvelope<ChatMessageChangedPayload> toEnvelope(ChatMessageDeletedEvent event) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.CHAT_MESSAGE_DELETED,
                event.tripId(),
                Instant.now(clock),
                new ChatMessageChangedPayload(event.messageId(), event.deletedAt())
        );
    }

    public RealtimeEventEnvelope<ChatMessageChangedPayload> toEnvelope(ChatReactionChangedEvent event) {
        return RealtimeEventEnvelope.create(
                RealtimeEventType.CHAT_REACTION_CHANGED,
                event.tripId(),
                Instant.now(clock),
                new ChatMessageChangedPayload(event.messageId(), null)
        );
    }
}
