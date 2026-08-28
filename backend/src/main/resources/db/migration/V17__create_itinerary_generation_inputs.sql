CREATE TABLE itinerary_generation_inputs (
    generation_id BIGINT PRIMARY KEY,
    snapshot_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT itinerary_generation_inputs_generation_fk
        FOREIGN KEY (generation_id)
        REFERENCES itinerary_generations(id)
        ON DELETE CASCADE,
    CONSTRAINT itinerary_generation_inputs_snapshot_version_check
        CHECK (snapshot_version >= 1)
);

COMMENT ON TABLE itinerary_generation_inputs IS '일정 생성 요청 시점의 여행 조건을 불변 JSON Snapshot으로 저장한다.';
COMMENT ON COLUMN itinerary_generation_inputs.generation_id IS 'Snapshot이 속한 일정 생성 작업 ID.';
COMMENT ON COLUMN itinerary_generation_inputs.snapshot_version IS 'Snapshot payload 구조 버전.';
COMMENT ON COLUMN itinerary_generation_inputs.payload IS '후보 수집, AI 요청, 응답 검증에 사용되는 요청 시점 여행 조건.';
COMMENT ON COLUMN itinerary_generation_inputs.created_at IS 'Snapshot 확정 시각.';
