-- WP-D: 여행방 대화. spec §5.1, 내부 순서 "저장/history/send" 단계.
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    author_user_id BIGINT REFERENCES users(id),
    type VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    client_message_id VARCHAR(100) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chat_messages_type_check
        CHECK (type IN ('USER_TEXT', 'SYSTEM_NOTICE'))
);

-- 같은 clientMessageId로 재전송된 send 요청은 새 행을 만들지 않고 기존 메시지를 그대로 반환한다.
CREATE UNIQUE INDEX chat_messages_trip_client_message_id_unique
    ON chat_messages (trip_id, client_message_id);

-- keyset pagination: WHERE trip_id = ? AND id < :cursor ORDER BY id DESC.
CREATE INDEX chat_messages_trip_id_id_idx ON chat_messages (trip_id, id DESC);

COMMENT ON TABLE chat_messages IS '여행방 대화 메시지. reconnect gap 복구·unread·삭제/답장/반응은 이후 phase에서 확장한다.';
COMMENT ON COLUMN chat_messages.author_user_id IS 'SYSTEM_NOTICE는 NULL.';
COMMENT ON COLUMN chat_messages.client_message_id IS '클라이언트가 생성한 idempotency key. (trip_id, client_message_id) 재전송은 200으로 기존 행을 반환한다.';
