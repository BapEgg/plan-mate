-- WP-C: 국내 자동차 route (Kakao Mobility 자동차 길찾기). spec §10.5:
-- provider = Kakao Mobility GET /v1/directions 고정, 일 10,000건 hard cap,
-- mode + 좌표 기준으로 cache한다. 같은 두 좌표 사이의 실제 도로 경로는 어느
-- itinerary·DAY에서 조회하든 동일하므로 좌표 단위로 캐시를 공유한다.

CREATE TABLE route_leg_snapshots (
    id BIGSERIAL PRIMARY KEY,
    travel_mode VARCHAR(20) NOT NULL,
    cache_key VARCHAR(120) NOT NULL,
    origin_latitude DOUBLE PRECISION NOT NULL,
    origin_longitude DOUBLE PRECISION NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    distance_meters INT NOT NULL,
    duration_seconds INT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX route_leg_snapshots_lookup_unique ON route_leg_snapshots (travel_mode, cache_key);

CREATE TABLE route_provider_daily_usage (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    usage_date DATE NOT NULL,
    call_count INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX route_provider_daily_usage_unique ON route_provider_daily_usage (provider, operation, usage_date);

COMMENT ON TABLE route_leg_snapshots IS '좌표쌍 + 이동수단 단위 route 결과 cache. 같은 두 지점을 다시 조회할 때 provider 호출을 재사용한다.';
COMMENT ON COLUMN route_leg_snapshots.cache_key IS 'origin/destination 좌표를 소수 5자리로 반올림해 만든 결정적 key.';
COMMENT ON TABLE route_provider_daily_usage IS 'KST 날짜 기준 provider 호출 원자적 카운터. 실패·timeout·retry도 호출 시도 시점에 먼저 증가시켜 무제한 재시도로 한도를 넘기지 않는다.';
