-- WP-B: 방장 이전 요청. spec §5.1 "방장 이전은 대상 MEMBER 수락 뒤 원자적으로 OWNER와 MEMBER 역할을 교체한다."
CREATE TABLE owner_transfer_requests (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    from_user_id BIGINT NOT NULL REFERENCES users(id),
    to_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT owner_transfer_requests_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT owner_transfer_requests_responded_at_consistency_check
        CHECK ((status = 'PENDING') = (responded_at IS NULL)),
    CONSTRAINT owner_transfer_requests_distinct_users_check
        CHECK (from_user_id <> to_user_id)
);

-- 한 여행방에 동시에 열려 있는 방장 이전 요청은 하나뿐이다.
CREATE UNIQUE INDEX owner_transfer_requests_pending_unique
    ON owner_transfer_requests (trip_id)
    WHERE status = 'PENDING';

CREATE INDEX owner_transfer_requests_to_user_status_idx ON owner_transfer_requests (to_user_id, status);

COMMENT ON TABLE owner_transfer_requests IS '방장 이전 요청. 발송 48시간 뒤 만료.';
