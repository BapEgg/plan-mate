package com.planmate.chat.api.event;

import com.planmate.chat.entity.ChatMessageType;
import java.time.Instant;

/**
 * WP-D: {@code realtime.chat}이 이 event를 받아 trip topic에 새 메시지를 broadcast한다. 같은
 * clientMessageId로 재전송된 idempotent replay에서는 발행하지 않는다 — 연결된 클라이언트가 같은
 * 메시지를 두 번 받지 않게 한다. 방송에 필요한 필드를 직접 담아, membership event와 같은 방식으로
 * realtime package가 chat repository/entity를 다시 조회하지 않게 한다.
 */
public record ChatMessageSentEvent(
        Long tripId,
        Long messageId,
        String clientMessageId,
        Long authorUserId,
        ChatMessageType type,
        String body,
        Instant sentAt
) {
}
