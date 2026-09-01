-- WP-D phase 3: 대화 읽음 표시. 읽은 지점을 이 interval(ACTIVE trip_members 행) 위에 직접
-- 저장한다 — 나가기/재가입은 새 행을 만들므로(ADR-0001) 읽음 상태도 interval과 함께 자연히
-- 초기화된다. joined_at이 이미 "현재 interval 시작"을 의미하므로 별도 컬럼이 필요 없다.
ALTER TABLE trip_members
    ADD COLUMN last_read_chat_message_id BIGINT REFERENCES chat_messages(id);

COMMENT ON COLUMN trip_members.last_read_chat_message_id IS '이 ACTIVE interval에서 마지막으로 읽은 chat_messages.id. NULL이면 이 interval에서 아직 아무것도 읽지 않은 상태.';
