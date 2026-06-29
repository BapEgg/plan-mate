UPDATE place_type_policies
SET reason = '기존 PlaceCandidateCollectionService 차단 타입에서 이전한 초기 정책입니다.',
    updated_at = NOW()
WHERE reason = 'Initial blocked type migrated from PlaceCandidateCollectionService.';

COMMENT ON TABLE place_type_policies IS '추천 후보 필터링에 사용하는 Google Places 타입별 운영 정책.';
COMMENT ON COLUMN place_type_policies.type_name IS 'Google Places 타입 이름. 예: lodging, tourist_attraction.';
COMMENT ON COLUMN place_type_policies.policy IS '타입별 정책값. BLOCK, PREFER, NEUTRAL 중 하나를 사용한다.';
COMMENT ON COLUMN place_type_policies.score_adjustment IS '차단하지 않는 정책에서 점수 보정에 사용할 값.';
COMMENT ON COLUMN place_type_policies.reason IS '해당 정책을 적용하는 운영상 사유.';
COMMENT ON COLUMN place_type_policies.enabled IS '후보 수집 시 이 정책을 조회하고 적용할지 여부.';
