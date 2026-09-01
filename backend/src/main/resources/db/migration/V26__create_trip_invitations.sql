-- WP-B: 내부 여행 초대. spec §5.1 "초대·친구·방장 이전"
CREATE TABLE trip_invitations (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    invitee_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_by_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT trip_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT trip_invitations_responded_at_consistency_check
        CHECK ((status = 'PENDING') = (responded_at IS NULL))
);

-- 같은 trip에 같은 사용자를 향한 PENDING 초대는 한 번에 하나만 허용한다.
CREATE UNIQUE INDEX trip_invitations_pending_unique
    ON trip_invitations (trip_id, invitee_user_id)
    WHERE status = 'PENDING';

CREATE INDEX trip_invitations_invitee_status_idx ON trip_invitations (invitee_user_id, status);
CREATE INDEX trip_invitations_trip_status_idx ON trip_invitations (trip_id, status);

COMMENT ON TABLE trip_invitations IS '여행방 내부 초대. 친구 관계와 독립적으로 수락한다.';
COMMENT ON COLUMN trip_invitations.status IS 'PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED.';
COMMENT ON COLUMN trip_invitations.expires_at IS '발송 7일 뒤 만료.';
