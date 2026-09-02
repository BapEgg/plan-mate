package com.planmate.realtime.chat;

import com.planmate.chat.api.event.ChatMessageSentEvent;
import com.planmate.chat.api.event.ChatMessageDeletedEvent;
import com.planmate.chat.api.event.ChatReactionChangedEvent;
import com.planmate.chat.api.event.ChatTypingChangedEvent;
import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.common.realtime.RealtimeEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.event.EventListener;

/**
 * WP-D: 새 대화 메시지를 trip topic에 broadcast한다. reconnect gap 복구·unread 갱신은 이후
 * phase에서 별도 event/구독자로 확장한다.
 */
@Component
public class ChatRealtimeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ChatRealtimeSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRealtimeEventMapper eventMapper;

    public ChatRealtimeSubscriber(SimpMessagingTemplate messagingTemplate, ChatRealtimeEventMapper eventMapper) {
        this.messagingTemplate = messagingTemplate;
        this.eventMapper = eventMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageSentEvent event) {
        publish(event.tripId(), event.messageId(), eventMapper.toEnvelope(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageDeletedEvent event) {
        publish(event.tripId(), event.messageId(), eventMapper.toEnvelope(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatReactionChangedEvent event) {
        publish(event.tripId(), event.messageId(), eventMapper.toEnvelope(event));
    }

    @EventListener
    public void handle(ChatTypingChangedEvent event) {
        publish(
                event.tripId(),
                event.memberId(),
                RealtimeEventEnvelope.create(
                        RealtimeEventType.CHAT_TYPING_UPDATED,
                        event.tripId(),
                        java.time.Instant.now(),
                        new ChatTypingChangedPayload(
                                event.memberId(), event.active(), event.expiresAtUtc(), event.eventSequence()
                        )
                )
        );
    }

    private void publish(Long tripId, Long messageId, Object envelope) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/trips/" + tripId + "/events",
                    envelope
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish chat realtime event. tripId={}, messageId={}",
                    tripId,
                    messageId,
                    exception
            );
        }
    }
}
