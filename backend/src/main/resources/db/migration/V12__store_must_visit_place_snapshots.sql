UPDATE trip_planning_profiles
SET must_visit_places = COALESCE((
    SELECT jsonb_agg(
        CASE
            WHEN jsonb_typeof(value) = 'string' THEN jsonb_build_object(
                'placeId', NULL,
                'name', value #>> '{}',
                'formattedAddress', NULL,
                'latitude', NULL,
                'longitude', NULL,
                'types', '[]'::jsonb,
                'primaryType', NULL
            )
            ELSE value
        END
    )
    FROM jsonb_array_elements(must_visit_places) AS value
), '[]'::jsonb);

COMMENT ON COLUMN trip_planning_profiles.must_visit_places IS '사용자가 꼭 방문하고 싶은 Google Places 장소 스냅샷 목록. placeId, 이름, 주소, 좌표, 타입을 포함하며 최대 5개까지 저장한다.';
