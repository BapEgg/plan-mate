-- WP-D: 답장, 5분 내 전체 삭제(tombstone), 메시지당 사용자 반응 한 건.
ALTER TABLE chat_messages
    ADD COLUMN reply_to_message_id BIGINT REFERENCES chat_messages(id) ON DELETE SET NULL,
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX chat_messages_reply_to_message_id_idx
    ON chat_messages (reply_to_message_id);

CREATE TABLE chat_message_reactions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chat_message_reactions_type_check
        CHECK (reaction_type IN ('LIKE', 'ACKNOWLEDGED')),
    CONSTRAINT chat_message_reactions_message_user_unique
        UNIQUE (message_id, user_id)
);

CREATE INDEX chat_message_reactions_message_id_idx
    ON chat_message_reactions (message_id);

COMMENT ON COLUMN chat_messages.reply_to_message_id IS '한 단계 답장 대상. UI는 이 메시지의 작성자와 본문 한 줄만 미리 본다.';
COMMENT ON COLUMN chat_messages.deleted_at IS '작성자가 server sent_at 기준 5분 안에 전체 삭제한 시각. body는 빈 문자열로 지우고 tombstone을 유지한다.';
COMMENT ON TABLE chat_message_reactions IS 'ACTIVE 멤버가 메시지에 남긴 반응. 사용자마다 메시지당 LIKE 또는 ACKNOWLEDGED 중 하나만 유지한다.';
