DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM itineraries
         GROUP BY generation_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce single itinerary per generation because duplicate generation_id rows exist.';
    END IF;
END
$$;

ALTER TABLE itineraries
    ADD CONSTRAINT itineraries_generation_unique
        UNIQUE (generation_id);

DROP INDEX IF EXISTS itineraries_generation_id_idx;

COMMENT ON CONSTRAINT itineraries_generation_unique
    ON itineraries
    IS '하나의 일정 생성 작업에는 최대 한 개의 확정 일정만 저장할 수 있다.';
