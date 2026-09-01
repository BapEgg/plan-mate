package com.planmate.realtime.chat;

import com.planmate.chat.api.event.ChatMessageSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
        try {
            messagingTemplate.convertAndSend(
                    "/topic/trips/" + event.tripId() + "/events",
                    eventMapper.toEnvelope(event)
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish chat realtime event. tripId={}, messageId={}",
                    event.tripId(),
                    event.messageId(),
                    exception
            );
        }
    }
}
