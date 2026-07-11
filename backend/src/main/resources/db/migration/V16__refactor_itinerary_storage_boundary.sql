ALTER TABLE itineraries
    DROP COLUMN IF EXISTS summary;

ALTER TABLE itinerary_items
    DROP COLUMN IF EXISTS place_name,
    DROP COLUMN IF EXISTS latitude,
    DROP COLUMN IF EXISTS longitude,
    DROP COLUMN IF EXISTS reason,
    ADD COLUMN IF NOT EXISTS created_source VARCHAR(40) NOT NULL DEFAULT 'AI_DRAFT';

COMMENT ON TABLE itineraries IS '검증된 일정 초안으로부터 저장된 PlanMate 일정 구조. Google 또는 AI가 생성한 표시 문구는 저장하지 않는다.';
COMMENT ON COLUMN itineraries.id IS '일정의 기본 키.';
COMMENT ON COLUMN itineraries.trip_id IS '이 일정을 소유한 여행방.';
COMMENT ON COLUMN itineraries.generation_id IS '이 일정을 만든 검증된 일정 생성 작업.';
COMMENT ON COLUMN itineraries.created_at IS '일정 저장 시각.';

COMMENT ON TABLE itinerary_items IS 'PlanMate 일정 구조와 Google Place ID만 저장하는 방문 항목. 장소명, 좌표, 평점 같은 표시 정보는 조회 시점에 다시 조회한다.';
COMMENT ON COLUMN itinerary_items.place_id IS 'Google Places Place ID. 일정 항목에 영구 저장하는 유일한 장소 식별자.';
COMMENT ON COLUMN itinerary_items.start_time IS '방문 예정 현지 시작 시각.';
COMMENT ON COLUMN itinerary_items.duration_minutes IS '방문 예정 체류 시간(분).';
COMMENT ON COLUMN itinerary_items.created_source IS '항목 생성 출처. 예: AI_DRAFT, USER_SELECTED, MANUAL_EDIT.';

COMMENT ON TABLE place_candidates IS '기존 일정 생성 후보 테이블. 신규 일정 저장 흐름에서는 이 테이블을 기준 데이터로 사용하지 않는다.';
