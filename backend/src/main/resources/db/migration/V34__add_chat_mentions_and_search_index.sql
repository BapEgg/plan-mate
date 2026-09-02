-- WP-D: member-id based mentions and room-scoped keyword search.
CREATE TABLE chat_message_mentions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    mentioned_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name_snapshot VARCHAR(100) NOT NULL,
    start_code_point INTEGER NOT NULL,
    end_code_point INTEGER NOT NULL,
    CONSTRAINT chat_message_mentions_range_check
        CHECK (start_code_point >= 0 AND end_code_point > start_code_point),
    CONSTRAINT chat_message_mentions_range_unique
        UNIQUE (message_id, start_code_point, end_code_point)
);

CREATE INDEX chat_message_mentions_message_id_idx
    ON chat_message_mentions (message_id);

CREATE INDEX chat_message_mentions_user_message_idx
    ON chat_message_mentions (mentioned_user_id, message_id DESC);

-- Search always narrows by trip/type/deletion before applying the literal substring predicate.
CREATE INDEX chat_messages_search_scope_idx
    ON chat_messages (trip_id, id DESC)
    WHERE type = 'USER_TEXT' AND deleted_at IS NULL;

COMMENT ON TABLE chat_message_mentions IS '현재 여행방 ACTIVE 참여자를 선택해 만든 개인 멘션. 본문 offset은 Unicode code point 기준이다.';
COMMENT ON COLUMN chat_message_mentions.display_name_snapshot IS '전송 당시 이름. 이후 이름이 바뀌어도 과거 본문과 offset을 보존한다.';
