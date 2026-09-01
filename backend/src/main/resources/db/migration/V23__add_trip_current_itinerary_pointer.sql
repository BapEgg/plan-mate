-- ADR-0002: immutable itinerary version과 current pointer
-- docs/adr/0002-itinerary-current-version.md

ALTER TABLE trips
    ADD COLUMN current_itinerary_id BIGINT REFERENCES itineraries(id);

ALTER TABLE itineraries
    ADD COLUMN version INT NOT NULL DEFAULT 1;

-- 기존 findFirstByTripIdOrderByCreatedAtDesc와 동일한 행을 가리키도록 백필한다.
UPDATE trips t
   SET current_itinerary_id = latest.id
  FROM (
      SELECT DISTINCT ON (trip_id) trip_id, id
        FROM itineraries
       ORDER BY trip_id, created_at DESC, id DESC
  ) latest
 WHERE t.id = latest.trip_id;

-- trip별 created_at 오름차순으로 순번을 매겨 version을 채운다.
UPDATE itineraries i
   SET version = ranked.rank
  FROM (
      SELECT id, ROW_NUMBER() OVER (
          PARTITION BY trip_id ORDER BY created_at ASC, id ASC
      ) AS rank
        FROM itineraries
  ) ranked
 WHERE i.id = ranked.id;

CREATE INDEX trips_current_itinerary_id_idx ON trips (current_itinerary_id);

COMMENT ON COLUMN trips.current_itinerary_id IS '지금 모든 멤버에게 기본으로 노출되는 itinerary version. NULL이면 아직 저장된 일정이 없다는 뜻이다(EMPTY_ITINERARY).';
COMMENT ON COLUMN itineraries.version IS 'trip 내 단조 증가하는 일정 버전 번호. created_at ASC 기준으로 1부터 시작한다.';
