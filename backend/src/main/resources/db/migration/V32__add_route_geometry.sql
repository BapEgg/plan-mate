ALTER TABLE route_leg_snapshots
    ADD COLUMN geometry TEXT;

COMMENT ON COLUMN route_leg_snapshots.geometry IS 'Kakao Directions roads.vertexes를 위도,경도 순서로 직렬화한 실제 자동차 경로 좌표열.';
