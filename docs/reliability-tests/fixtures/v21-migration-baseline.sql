-- Deterministic WP-G migration fixture.
-- Apply only after Flyway has migrated an isolated PostgreSQL database to V21.
-- IDs mirror the documented local baseline, but every value is synthetic.

BEGIN;

INSERT INTO users (
    id, email, email_canonical, nickname, role, status, email_verified_at,
    created_at, updated_at
)
VALUES
    (2588, 'migration-owner@example.invalid', 'migration-owner@example.invalid', '마이그레이션 방장', 'USER', 'ACTIVE', NOW(), NOW(), NOW()),
    (2623, 'migration-member1@example.invalid', 'migration-member1@example.invalid', '마이그레이션 멤버1', 'USER', 'ACTIVE', NOW(), NOW(), NOW()),
    (2624, 'migration-member2@example.invalid', 'migration-member2@example.invalid', '마이그레이션 멤버2', 'USER', 'ACTIVE', NOW(), NOW(), NOW());

INSERT INTO trips (
    id, title, destination, start_date, end_date, created_by,
    destination_place_id, destination_formatted_address,
    destination_latitude, destination_longitude, destination_types,
    destination_primary_type, created_at, updated_at
)
VALUES (
    1530, 'V21 거제도 여행', '거제시', DATE '2026-10-10', DATE '2026-10-13', 2588,
    'v21-geoje-destination', '경상남도 거제시', 34.8800, 128.6200,
    '["locality"]'::jsonb, 'locality', TIMESTAMPTZ '2026-08-01 00:00:00+00', TIMESTAMPTZ '2026-08-01 00:00:00+00'
);

INSERT INTO trip_members (id, trip_id, user_id, role, created_at)
VALUES
    (3001, 1530, 2588, 'OWNER', TIMESTAMPTZ '2026-08-01 00:00:00+00'),
    (3002, 1530, 2623, 'MEMBER', TIMESTAMPTZ '2026-08-02 00:00:00+00'),
    (3003, 1530, 2624, 'MEMBER', TIMESTAMPTZ '2026-08-03 00:00:00+00');

INSERT INTO itinerary_generations (
    id, trip_id, status, prompt_version, created_at, updated_at
)
VALUES (
    1415, 1530, 'COMPLETED', 'migration-v21-fixture',
    TIMESTAMPTZ '2026-08-04 00:00:00+00', TIMESTAMPTZ '2026-08-04 00:00:00+00'
);

INSERT INTO itineraries (id, trip_id, generation_id, created_at)
VALUES (505, 1530, 1415, TIMESTAMPTZ '2026-08-05 00:00:00+00');

INSERT INTO itinerary_days (id, itinerary_id, day, date)
VALUES
    (5051, 505, 1, DATE '2026-10-10'),
    (5052, 505, 2, DATE '2026-10-11'),
    (5053, 505, 3, DATE '2026-10-12'),
    (5054, 505, 4, DATE '2026-10-13');

INSERT INTO itinerary_items (
    id, day_id, sequence, place_id, start_time, duration_minutes, created_source
)
SELECT
    day_id * 100 + sequence,
    day_id,
    sequence,
    'v21-place-' || (day_id - 5050) || '-' || sequence,
    (TIME '08:00' + sequence * INTERVAL '90 minutes')::time,
    60,
    'AI_DRAFT'
FROM (VALUES (5051, 5), (5052, 5), (5053, 7), (5054, 6)) AS schedule(day_id, item_count)
CROSS JOIN LATERAL generate_series(1, item_count) AS sequence;

COMMIT;
