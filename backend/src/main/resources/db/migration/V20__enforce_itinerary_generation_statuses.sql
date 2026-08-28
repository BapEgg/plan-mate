DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM itinerary_generations
         WHERE status IN ('PLANNING', 'VALIDATING')
    ) THEN
        RAISE EXCEPTION
            'Legacy itinerary generation statuses PLANNING or VALIDATING exist. Resolve them before applying V20.';
    END IF;
END
$$;

ALTER TABLE itinerary_generations
    ADD CONSTRAINT itinerary_generations_status_check
        CHECK (
            status IN (
                'CREATED',
                'COLLECTING_CANDIDATES',
                'READY_FOR_PLANNING',
                'COMPLETED',
                'FAILED'
            )
        );

COMMENT ON CONSTRAINT itinerary_generations_status_check
    ON itinerary_generations
    IS 'Restricts itinerary generation statuses for the manual handoff flow.';
