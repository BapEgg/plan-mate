-- ADR-0005: UTC instant와 여행 lifecycle timezone
-- docs/adr/0005-utc-instant-lifecycle-timezone.md

ALTER TABLE trips
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';

-- 기존 trip은 전부 국내 여행이므로 실제 사실에 맞는 기본값으로 백필한다.
UPDATE trips
   SET timezone = 'Asia/Seoul'
 WHERE timezone IS NULL;

COMMENT ON COLUMN trips.timezone IS 'IANA timezone id. 여행 lifecycle(UPCOMING/ONGOING/COMPLETED), chat cutoff, vote deadline 계산의 기준 zone.';
