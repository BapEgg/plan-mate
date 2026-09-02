package com.planmate.realtime.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.chat.api.event.ChatMessageDeletedEvent;
import com.planmate.chat.api.event.ChatMessageSentEvent;
import com.planmate.chat.api.event.ChatReactionChangedEvent;
import com.planmate.chat.entity.ChatMessageType;
import com.planmate.common.realtime.RealtimeEventEnvelope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRealtimeEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private final ChatRealtimeEventMapper mapper = new ChatRealtimeEventMapper(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sentEventCarriesAOneLevelReplyPreview() {
        RealtimeEventEnvelope<ChatMessageSentPayload> envelope = mapper.toEnvelope(new ChatMessageSentEvent(
                10L,
                21L,
                "client-21",
                2L,
                ChatMessageType.USER_TEXT,
                "답장입니다",
                NOW.minusSeconds(1),
                20L,
                1L,
                "원문 한 줄",
                false,
                List.of()
        ));

        assertThat(envelope.type()).isEqualTo("CHAT_MESSAGE_SENT");
        assertThat(envelope.payload().replyToMessageId()).isEqualTo(20L);
        assertThat(envelope.payload().replyAuthorUserId()).isEqualTo(1L);
        assertThat(envelope.payload().replyBody()).isEqualTo("원문 한 줄");
        assertThat(envelope.payload().replyDeleted()).isFalse();
    }

    @Test
    void deletedEventOnlySignalsTheMessageToRefresh() {
        RealtimeEventEnvelope<ChatMessageChangedPayload> envelope = mapper.toEnvelope(
                new ChatMessageDeletedEvent(10L, 21L, NOW.minusSeconds(2))
        );

        assertThat(envelope.type()).isEqualTo("CHAT_MESSAGE_DELETED");
        assertThat(envelope.payload().messageId()).isEqualTo(21L);
        assertThat(envelope.payload().deletedAt()).isEqualTo(NOW.minusSeconds(2));
    }

    @Test
    void reactionEventSignalsTheMessageWithoutLeakingViewerSpecificState() {
        RealtimeEventEnvelope<ChatMessageChangedPayload> envelope = mapper.toEnvelope(
                new ChatReactionChangedEvent(10L, 21L)
        );

        assertThat(envelope.type()).isEqualTo("CHAT_REACTION_CHANGED");
        assertThat(envelope.payload().messageId()).isEqualTo(21L);
        assertThat(envelope.payload().deletedAt()).isNull();
    }
}
