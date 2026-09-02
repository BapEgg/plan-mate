-- WP-F: OWNER가 요청한 전체/부분 일정 다시 만들기 작업과 적용 전 초안을 보존한다.
-- 실제 itinerary current pointer는 검토/적용 command 전까지 바뀌지 않는다.

CREATE TABLE itinerary_regenerations (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    generation_id BIGINT NOT NULL UNIQUE REFERENCES itinerary_generations(id),
    base_itinerary_id BIGINT NOT NULL REFERENCES itineraries(id),
    base_itinerary_version INT NOT NULL,
    requested_by_user_id BIGINT NOT NULL REFERENCES users(id),
    scope VARCHAR(16) NOT NULL,
    day_number INT,
    start_item_id BIGINT REFERENCES itinerary_items(id),
    end_item_id BIGINT REFERENCES itinerary_items(id),
    fixed_item_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    additional_request VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    draft_payload JSONB,
    failure_reason VARCHAR(500),
    applied_itinerary_id BIGINT REFERENCES itineraries(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT itinerary_regeneration_scope_check CHECK (scope IN ('FULL', 'PARTIAL')),
    CONSTRAINT itinerary_regeneration_status_check CHECK (status IN (
        'GENERATING', 'READY_FOR_REVIEW', 'APPLIED', 'REJECTED', 'FAILED', 'STALE'
    )),
    CONSTRAINT itinerary_regeneration_partial_fields_check CHECK (
        (scope = 'FULL' AND day_number IS NULL AND start_item_id IS NULL AND end_item_id IS NULL)
        OR
        (scope = 'PARTIAL' AND day_number IS NOT NULL AND start_item_id IS NOT NULL AND end_item_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX itinerary_regenerations_trip_active_unique
    ON itinerary_regenerations (trip_id)
    WHERE status IN ('GENERATING', 'READY_FOR_REVIEW');

CREATE INDEX itinerary_regenerations_trip_created_idx
    ON itinerary_regenerations (trip_id, created_at DESC, id DESC);

COMMENT ON TABLE itinerary_regenerations IS '기존 current 일정을 유지한 채 생성·검토하는 전체/부분 일정 초안.';
