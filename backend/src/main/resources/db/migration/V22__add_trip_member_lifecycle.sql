-- ADR-0001: membership interval과 단일 ACTIVE OWNER
-- docs/adr/0001-membership-interval.md

ALTER TABLE trip_members
    DROP CONSTRAINT IF EXISTS trip_members_trip_user_unique;

ALTER TABLE trip_members
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN joined_at TIMESTAMPTZ,
    ADD COLUMN left_at TIMESTAMPTZ,
    ADD COLUMN left_reason VARCHAR(20);

UPDATE trip_members
   SET joined_at = created_at
 WHERE joined_at IS NULL;

ALTER TABLE trip_members
    ALTER COLUMN joined_at SET NOT NULL;

ALTER TABLE trip_members
    ADD CONSTRAINT trip_members_status_check
        CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    ADD CONSTRAINT trip_members_left_reason_check
        CHECK (left_reason IS NULL OR left_reason IN ('LEFT', 'REMOVED')),
    ADD CONSTRAINT trip_members_left_at_consistency_check
        CHECK ((status = 'ACTIVE') = (left_at IS NULL));

CREATE UNIQUE INDEX trip_members_active_user_unique
    ON trip_members (trip_id, user_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX trip_members_active_owner_unique
    ON trip_members (trip_id)
    WHERE status = 'ACTIVE' AND role = 'OWNER';

CREATE INDEX trip_members_trip_status_idx ON trip_members (trip_id, status);

COMMENT ON COLUMN trip_members.status IS 'membership interval 상태: ACTIVE, LEFT, REMOVED. 나가기/내보내기는 새 행이 아니라 같은 행을 종료 상태로 갱신하고, 재가입은 새 행을 추가한다.';
COMMENT ON COLUMN trip_members.joined_at IS '이 membership interval이 시작된 시각 (기존 행은 created_at으로 백필됨).';
COMMENT ON COLUMN trip_members.left_at IS '이 membership interval이 종료된 시각. status=ACTIVE이면 NULL이어야 한다.';
COMMENT ON COLUMN trip_members.left_reason IS '종료 사유: LEFT(본인 나가기), REMOVED(OWNER가 내보냄). 방장 이전은 interval을 종료하지 않고 같은 행의 role만 교체한다.';
