-- WP-B: 친구 관계. 여행방 멤버십과 독립적으로 수락한다(spec §5.1).
CREATE TABLE friend_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT friend_requests_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT friend_requests_responded_at_consistency_check
        CHECK ((status = 'PENDING') = (responded_at IS NULL)),
    CONSTRAINT friend_requests_distinct_users_check
        CHECK (requester_user_id <> addressee_user_id)
);

-- 같은 두 사용자 사이에 방향과 무관하게 PENDING 요청은 하나만 허용한다.
CREATE UNIQUE INDEX friend_requests_pending_pair_unique
    ON friend_requests (LEAST(requester_user_id, addressee_user_id), GREATEST(requester_user_id, addressee_user_id))
    WHERE status = 'PENDING';

CREATE INDEX friend_requests_addressee_status_idx ON friend_requests (addressee_user_id, status);
CREATE INDEX friend_requests_requester_status_idx ON friend_requests (requester_user_id, status);

COMMENT ON TABLE friend_requests IS '친구 요청/관계. ACCEPTED 행의 존재 자체가 친구 관계를 의미하며 별도 friendship table을 두지 않는다.';
