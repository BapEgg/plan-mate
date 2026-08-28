ALTER TABLE itinerary_generations
    ADD COLUMN collection_claim_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN collection_lease_expires_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN itinerary_generations.collection_claim_version IS
    '현재 후보 수집 Worker claim의 fencing version';

COMMENT ON COLUMN itinerary_generations.collection_lease_expires_at IS
    '현재 후보 수집 Worker claim의 lease 만료 시각';
