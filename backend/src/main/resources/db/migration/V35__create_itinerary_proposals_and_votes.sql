-- WP-E: 일정 변경안, 유권자 snapshot, ballot, immutable revision metadata.
-- 일정 revision은 AI generation이 아니므로 itineraries.generation_id를 nullable로 연다.

ALTER TABLE itineraries
    ALTER COLUMN generation_id DROP NOT NULL;

ALTER TABLE itineraries
    ADD COLUMN base_itinerary_id BIGINT REFERENCES itineraries(id),
    ADD COLUMN proposal_id BIGINT,
    ADD COLUMN revision_source VARCHAR(32),
    ADD COLUMN revised_by_user_id BIGINT REFERENCES users(id);

CREATE TABLE itinerary_proposals (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    base_itinerary_id BIGINT NOT NULL REFERENCES itineraries(id),
    base_itinerary_version INT NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    proposal_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    decision_mode VARCHAR(16),
    day_number INT NOT NULL,
    target_item_id BIGINT NOT NULL REFERENCES itinerary_items(id),
    replacement_place_id VARCHAR(255) NOT NULL,
    replacement_display_name VARCHAR(255) NOT NULL,
    replacement_start_time TIME NOT NULL,
    replacement_duration_minutes INT NOT NULL,
    canonical_fingerprint VARCHAR(64) NOT NULL,
    applied_itinerary_id BIGINT REFERENCES itineraries(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT itinerary_proposal_duration_check CHECK (replacement_duration_minutes BETWEEN 15 AND 720),
    CONSTRAINT itinerary_proposal_status_check CHECK (status IN ('READY', 'VOTE_OPEN', 'APPLIED', 'REJECTED', 'CANCELLED', 'STALE')),
    CONSTRAINT itinerary_proposal_mode_check CHECK (decision_mode IS NULL OR decision_mode IN ('DIRECT', 'VOTE'))
);

ALTER TABLE itineraries
    ADD CONSTRAINT itineraries_proposal_fk
    FOREIGN KEY (proposal_id) REFERENCES itinerary_proposals(id);

CREATE INDEX itinerary_proposals_trip_created_idx
    ON itinerary_proposals (trip_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX itinerary_proposals_active_fingerprint_unique
    ON itinerary_proposals (trip_id, canonical_fingerprint)
    WHERE status IN ('READY', 'VOTE_OPEN');

CREATE TABLE itinerary_votes (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    proposal_id BIGINT NOT NULL UNIQUE REFERENCES itinerary_proposals(id) ON DELETE CASCADE,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(40) NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    result_reason VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT itinerary_vote_status_check CHECK (status IN (
        'OPEN', 'PASSED', 'REJECTED', 'INSUFFICIENT_PARTICIPATION', 'CANCELLED', 'STALE'
    ))
);

CREATE INDEX itinerary_votes_trip_status_deadline_idx
    ON itinerary_votes (trip_id, status, deadline, id);

CREATE TABLE itinerary_vote_voters (
    id BIGSERIAL PRIMARY KEY,
    vote_id BIGINT NOT NULL REFERENCES itinerary_votes(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (vote_id, user_id)
);

CREATE TABLE itinerary_vote_ballots (
    id BIGSERIAL PRIMARY KEY,
    vote_id BIGINT NOT NULL REFERENCES itinerary_votes(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    choice VARCHAR(24) NOT NULL,
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    cast_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT itinerary_vote_ballot_choice_check CHECK (choice IN ('CHANGE', 'KEEP_CURRENT')),
    UNIQUE (vote_id, user_id)
);

CREATE INDEX itinerary_vote_ballots_vote_valid_idx
    ON itinerary_vote_ballots (vote_id, valid);

COMMENT ON TABLE itinerary_proposals IS '현재 일정을 바로 바꾸지 않는 검증된 변경안. base itinerary guard 뒤에만 적용한다.';
COMMENT ON TABLE itinerary_votes IS 'OPEN 시점 ACTIVE 멤버를 voter snapshot으로 고정하는 일정 변경 투표.';
COMMENT ON TABLE itinerary_vote_voters IS '투표 시작 당시 유권자 snapshot. 멤버십 종료 시 invalid 처리한다.';
COMMENT ON TABLE itinerary_vote_ballots IS '유권자별 마지막 한 표. 변경은 같은 row update, 무효화 이력은 valid=false로 보존한다.';
