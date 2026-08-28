ALTER TABLE place_candidates
    ADD COLUMN types JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN business_status VARCHAR(40),
    ADD COLUMN forced_must_visit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN distance_meters DOUBLE PRECISION;

UPDATE place_candidates
   SET forced_must_visit = TRUE
 WHERE source_categories ? 'MUST_VISIT';

ALTER TABLE place_candidates
    ADD CONSTRAINT place_candidates_generation_rank_unique
        UNIQUE (generation_id, rank);

COMMENT ON TABLE place_candidates IS '특정 일정 생성 작업에서 실제 후보 수집과 추천에 사용된 장소 후보 Snapshot. AI 요청과 응답 검증은 이 확정 후보 집합을 기준으로 한다.';
COMMENT ON COLUMN place_candidates.id IS '저장된 후보 Snapshot의 기본 키.';
COMMENT ON COLUMN place_candidates.generation_id IS '후보 Snapshot이 속한 일정 생성 작업 ID.';
COMMENT ON COLUMN place_candidates.place_id IS '후보 장소의 Provider Place ID.';
COMMENT ON COLUMN place_candidates.name IS '후보 장소의 표시 이름.';
COMMENT ON COLUMN place_candidates.address IS '후보 장소의 정규화된 주소.';
COMMENT ON COLUMN place_candidates.latitude IS '후보 장소의 위도.';
COMMENT ON COLUMN place_candidates.longitude IS '후보 장소의 경도.';
COMMENT ON COLUMN place_candidates.primary_type IS '후보 장소의 대표 place type.';
COMMENT ON COLUMN place_candidates.types IS '후보 장소의 전체 place type 목록.';
COMMENT ON COLUMN place_candidates.business_status IS 'Provider가 반환한 후보 장소의 영업 상태.';
COMMENT ON COLUMN place_candidates.rating IS '후보 점수화에 사용된 Provider 평점.';
COMMENT ON COLUMN place_candidates.user_rating_count IS '후보 점수화에 사용된 Provider 리뷰 수.';
COMMENT ON COLUMN place_candidates.source_categories IS '후보가 발견되거나 강제 포함된 추천 카테고리 목록.';
COMMENT ON COLUMN place_candidates.opening_periods IS 'AI 요청에 전달할 수 있는 축약 영업시간 정보.';
COMMENT ON COLUMN place_candidates.forced_must_visit IS '필수 방문지 조건에 의해 강제 포함된 후보인지 여부.';
COMMENT ON COLUMN place_candidates.distance_meters IS '검색 기준점에서 후보 장소까지의 거리 미터 값.';
COMMENT ON COLUMN place_candidates.score IS '후보 정렬과 선정을 위해 계산한 상대 점수.';
COMMENT ON COLUMN place_candidates.rank IS '최종 후보 목록에서의 1부터 시작하는 순위.';
