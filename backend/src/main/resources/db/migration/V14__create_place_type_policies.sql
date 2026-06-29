CREATE TABLE place_type_policies (
    id BIGSERIAL PRIMARY KEY,
    type_name VARCHAR(100) NOT NULL,
    policy VARCHAR(20) NOT NULL,
    score_adjustment DOUBLE PRECISION NOT NULL DEFAULT 0,
    reason VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT place_type_policies_type_name_unique UNIQUE (type_name),
    CONSTRAINT place_type_policies_policy_check CHECK (policy IN ('BLOCK', 'PREFER', 'NEUTRAL'))
);

CREATE INDEX place_type_policies_enabled_idx
    ON place_type_policies(enabled);

CREATE INDEX place_type_policies_enabled_policy_idx
    ON place_type_policies(enabled, policy);

INSERT INTO place_type_policies (type_name, policy, score_adjustment, reason, enabled)
VALUES
    ('locality', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('political', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('country', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('administrative_area_level_1', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('administrative_area_level_2', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('administrative_area_level_3', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('postal_code', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('route', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('street_address', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('lodging', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('parking', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('atm', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('bank', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('gas_station', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE),
    ('real_estate_agency', 'BLOCK', 0, 'Initial blocked type migrated from PlaceCandidateCollectionService.', TRUE);

COMMENT ON TABLE place_type_policies IS 'Runtime policy for Google place types used by recommendation candidate filtering.';
COMMENT ON COLUMN place_type_policies.type_name IS 'Google Places type name, such as lodging or tourist_attraction.';
COMMENT ON COLUMN place_type_policies.policy IS 'Filtering or scoring policy: BLOCK, PREFER, or NEUTRAL.';
COMMENT ON COLUMN place_type_policies.score_adjustment IS 'Reserved score adjustment for non-block policies.';
COMMENT ON COLUMN place_type_policies.reason IS 'Operational reason for this policy.';
COMMENT ON COLUMN place_type_policies.enabled IS 'Whether this policy is loaded during candidate collection.';
