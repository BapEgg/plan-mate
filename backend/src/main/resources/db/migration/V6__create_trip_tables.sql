CREATE TABLE trips (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(60) NOT NULL,
    destination VARCHAR(60) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT trips_date_range_check CHECK (end_date >= start_date)
);

CREATE INDEX trips_created_by_idx ON trips(created_by);

CREATE TABLE trip_members (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT trip_members_trip_user_unique UNIQUE (trip_id, user_id)
);

CREATE INDEX trip_members_user_id_idx ON trip_members(user_id);
CREATE INDEX trip_members_trip_id_idx ON trip_members(trip_id);
