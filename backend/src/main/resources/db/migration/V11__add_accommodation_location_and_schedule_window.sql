ALTER TABLE trip_planning_profiles
    ADD COLUMN IF NOT EXISTS accommodation_place_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS accommodation_formatted_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS accommodation_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS accommodation_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS accommodation_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS accommodation_primary_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS daily_start_time TIME NOT NULL DEFAULT '08:00',
    ADD COLUMN IF NOT EXISTS daily_end_time TIME NOT NULL DEFAULT '20:00';

ALTER TABLE trip_planning_profiles
    DROP CONSTRAINT IF EXISTS trip_planning_profiles_selected_accommodation_check;

ALTER TABLE trip_planning_profiles
    ADD CONSTRAINT trip_planning_profiles_accommodation_snapshot_check CHECK (
        (
            accommodation_mode = 'UNDECIDED'
            AND accommodation_area IS NOT NULL
            AND accommodation_place_id IS NULL
            AND accommodation_latitude IS NULL
            AND accommodation_longitude IS NULL
        )
        OR
        (
            accommodation_mode = 'PLACE_SEARCH'
            AND accommodation_area IS NULL
            AND accommodation_place_id IS NOT NULL
            AND accommodation_name IS NOT NULL
            AND accommodation_latitude IS NOT NULL
            AND accommodation_longitude IS NOT NULL
        )
    );

ALTER TABLE trip_planning_profiles
    ADD CONSTRAINT trip_planning_profiles_daily_time_range_check CHECK (daily_start_time < daily_end_time);

COMMENT ON COLUMN trip_planning_profiles.accommodation_name IS '숙소를 Google Places에서 선택한 경우 Google Place Details의 displayName을 저장한다. 숙소 미정이면 null이다.';
COMMENT ON COLUMN trip_planning_profiles.accommodation_place_id IS '숙소로 선택한 Google Places Place ID. 숙소 미정이면 null이다.';
COMMENT ON COLUMN trip_planning_profiles.accommodation_formatted_address IS '숙소로 선택한 장소의 Google Place Details formattedAddress.';
COMMENT ON COLUMN trip_planning_profiles.accommodation_latitude IS '숙소로 선택한 장소의 위도. 후보지 검색과 향후 동선 계산 기준점으로 사용한다.';
COMMENT ON COLUMN trip_planning_profiles.accommodation_longitude IS '숙소로 선택한 장소의 경도. 후보지 검색과 향후 동선 계산 기준점으로 사용한다.';
COMMENT ON COLUMN trip_planning_profiles.accommodation_types IS '숙소로 선택한 장소의 Google place type 목록. 검증 차단이 아니라 표시와 참고용으로 저장한다.';
COMMENT ON COLUMN trip_planning_profiles.accommodation_primary_type IS '숙소로 선택한 장소의 Google primaryType. 표시와 참고용으로 저장한다.';
COMMENT ON COLUMN trip_planning_profiles.daily_start_time IS '하루 일정을 배치할 수 있는 시작 시간. 사용자가 비워두면 서버 기본값을 저장한다.';
COMMENT ON COLUMN trip_planning_profiles.daily_end_time IS '하루 일정을 배치할 수 있는 종료 시간. 사용자가 비워두면 서버 기본값을 저장한다.';
