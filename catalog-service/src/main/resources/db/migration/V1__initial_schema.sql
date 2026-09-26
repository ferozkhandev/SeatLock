CREATE TABLE venue (
                       id UUID PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       city VARCHAR(100) NOT NULL,
                       address TEXT NOT NULL,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE venue_section (
                               id UUID PRIMARY KEY,
                               venue_id UUID NOT NULL,
                               name VARCHAR(100) NOT NULL,
                               row_count INT NOT NULL,
                               seats_per_row INT NOT NULL,
                               price_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00,
                               CONSTRAINT fk_venue_section_venue
                                   FOREIGN KEY (venue_id)
                                       REFERENCES venue(id)
                                       ON DELETE CASCADE
);

CREATE TABLE event (
                       id UUID PRIMARY KEY,
                       venue_id UUID NOT NULL,
                       organizer_id UUID NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       description TEXT,
                       category VARCHAR(50) NOT NULL,
                       starts_at TIMESTAMPTZ NOT NULL,
                       ends_at TIMESTAMPTZ NOT NULL,
                       base_price NUMERIC(10,2) NOT NULL,
                       status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                       poster_key VARCHAR(255),
                       version INT NOT NULL DEFAULT 0,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       CONSTRAINT fk_event_venue
                           FOREIGN KEY (venue_id)
                               REFERENCES venue(id)
);

CREATE TABLE outbox_event (
                              id UUID PRIMARY KEY,
                              aggregate_type VARCHAR(50) NOT NULL,
                              aggregate_id UUID NOT NULL,
                              event_type VARCHAR(50) NOT NULL,
                              payload JSONB NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              published_at TIMESTAMPTZ
);

-- It is to search events by status and starts_at, so that we can find events for a specific status in a specific time range
CREATE INDEX idx_event_status_starts_at
    ON event (status, starts_at);

-- It is to search events by category and starts_at, so that we can find events for a specific category in a specific time range
CREATE INDEX idx_event_category_starts_at
    ON event (category, starts_at);

-- It is to search events by venue and starts_at, so that we can find events for a specific venue in a specific time range
CREATE INDEX idx_event_venue_starts_at
    ON event(venue_id, starts_at);

-- It is to search venues by city
CREATE INDEX idx_venue_city
    ON venue (city);

-- It is to search outbox events that are not published yet. if published at is not null,
-- it means that the event is delivered to kafka.
CREATE INDEX idx_outbox_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;

-- Wrote this for searching events by name and description
CREATE INDEX idx_event_search
    ON event USING GIN (to_tsvector('english', name || ' ' || coalesce(description, '')));