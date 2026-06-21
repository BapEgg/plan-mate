ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS destination_place_id VARCHAR(255);
