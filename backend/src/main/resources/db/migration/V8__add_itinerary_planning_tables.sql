ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS destination_formatted_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS destination_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destination_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destination_viewport_low_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destination_viewport_low_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destination_viewport_high_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destination_viewport_high_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destination_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS destination_primary_type VARCHAR(100);

CREATE TABLE trip_planning_profiles (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    companion_count INTEGER NOT NULL,
    companion_type VARCHAR(30) NOT NULL,
    has_children BOOLEAN NOT NULL,
    child_count INTEGER NOT NULL,
    child_age_group VARCHAR(30),
    has_seniors BOOLEAN NOT NULL,
    senior_count INTEGER NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    budget_amount BIGINT,
    budget_level VARCHAR(30) NOT NULL,
    included_budget_items JSONB NOT NULL,
    travel_pace VARCHAR(30) NOT NULL,
    interests JSONB NOT NULL,
    primary_transport_mode VARCHAR(30) NOT NULL,
    secondary_transport_modes JSONB NOT NULL,
    accommodation_mode VARCHAR(30) NOT NULL,
    accommodation_area VARCHAR(30),
    accommodation_name VARCHAR(120),
    check_in_time TIME,
    check_out_time TIME,
    must_visit_places JSONB NOT NULL,
    avoid_conditions JSONB NOT NULL,
    free_request VARCHAR(800),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT trip_planning_profiles_trip_unique UNIQUE (trip_id),
    CONSTRAINT trip_planning_profiles_companion_count_check CHECK (companion_count >= 1),
    CONSTRAINT trip_planning_profiles_dependent_count_check CHECK (child_count + senior_count <= companion_count),
    CONSTRAINT trip_planning_profiles_budget_amount_check CHECK (budget_amount IS NULL OR budget_amount > 0),
    CONSTRAINT trip_planning_profiles_interests_count_check CHECK (jsonb_array_length(interests) BETWEEN 1 AND 5),
    CONSTRAINT trip_planning_profiles_must_visit_count_check CHECK (jsonb_array_length(must_visit_places) <= 5),
    CONSTRAINT trip_planning_profiles_selected_accommodation_check CHECK (
        accommodation_mode <> 'PLACE_SEARCH'
        OR (accommodation_name IS NOT NULL AND check_in_time IS NOT NULL AND check_out_time IS NOT NULL)
    )
);

CREATE INDEX trip_planning_profiles_trip_id_idx ON trip_planning_profiles(trip_id);

CREATE TABLE itinerary_generations (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX itinerary_generations_trip_id_idx ON itinerary_generations(trip_id);
CREATE INDEX itinerary_generations_status_idx ON itinerary_generations(status);

CREATE TABLE place_candidates (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES itinerary_generations(id) ON DELETE CASCADE,
    place_id VARCHAR(255) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    primary_type VARCHAR(100),
    rating DOUBLE PRECISION,
    user_rating_count INTEGER,
    source_categories JSONB NOT NULL,
    opening_periods JSONB NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    rank INTEGER NOT NULL,
    CONSTRAINT place_candidates_generation_place_unique UNIQUE (generation_id, place_id),
    CONSTRAINT place_candidates_rank_check CHECK (rank >= 1)
);

CREATE INDEX place_candidates_generation_id_idx ON place_candidates(generation_id);
CREATE INDEX place_candidates_place_id_idx ON place_candidates(place_id);

CREATE TABLE itineraries (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    generation_id BIGINT NOT NULL REFERENCES itinerary_generations(id) ON DELETE CASCADE,
    summary VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX itineraries_trip_id_idx ON itineraries(trip_id);
CREATE INDEX itineraries_generation_id_idx ON itineraries(generation_id);

CREATE TABLE itinerary_days (
    id BIGSERIAL PRIMARY KEY,
    itinerary_id BIGINT NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    day INTEGER NOT NULL,
    date DATE NOT NULL,
    CONSTRAINT itinerary_days_itinerary_day_unique UNIQUE (itinerary_id, day),
    CONSTRAINT itinerary_days_day_check CHECK (day >= 1)
);

CREATE INDEX itinerary_days_itinerary_id_idx ON itinerary_days(itinerary_id);

CREATE TABLE itinerary_items (
    id BIGSERIAL PRIMARY KEY,
    day_id BIGINT NOT NULL REFERENCES itinerary_days(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    place_id VARCHAR(255) NOT NULL,
    place_name VARCHAR(200) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    start_time TIME NOT NULL,
    duration_minutes INTEGER NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT itinerary_items_day_sequence_unique UNIQUE (day_id, sequence),
    CONSTRAINT itinerary_items_sequence_check CHECK (sequence >= 1),
    CONSTRAINT itinerary_items_duration_check CHECK (duration_minutes > 0)
);

CREATE INDEX itinerary_items_day_id_idx ON itinerary_items(day_id);
CREATE INDEX itinerary_items_place_id_idx ON itinerary_items(place_id);
