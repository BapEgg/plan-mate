-- ADR-0004: outbox dispatcher / broker delivery topology
-- docs/adr/0004-outbox-broker-delivery-topology.md

ALTER TABLE outbox_events
    ADD COLUMN dispatched_at TIMESTAMPTZ,
    ADD COLUMN dispatch_attempts INT NOT NULL DEFAULT 0;

CREATE INDEX outbox_events_undispatched_idx ON outbox_events (created_at)
    WHERE dispatched_at IS NULL;

COMMENT ON COLUMN outbox_events.dispatched_at IS 'AFTER_COMMIT realtime publish가 성공적으로 완료된 시각. NULL이면 아직 전달되지 않았거나 전달 여부를 알 수 없다(crash 복구 대상).';
COMMENT ON COLUMN outbox_events.dispatch_attempts IS 'realtime publish 재시도 횟수. reconciliation job(기본 비활성)이 사용한다.';
