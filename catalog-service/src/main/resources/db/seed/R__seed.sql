-- These fixed IDs belong to the dev seed, so running it again does not create duplicate venues
INSERT INTO venue (
                      id,
                      name,
                      city,
                      address
)
VALUES ('a0000000-0000-0000-0000-000000000001', 'Lahore Arts Hall', 'Lahore', '12 Mall Road'),
       ('a0000000-0000-0000-0000-000000000002', 'Lahore Sports Arena', 'Lahore', '24 Stadium Road'),
       ('a0000000-0000-0000-0000-000000000003', 'Karachi Music Hall', 'Karachi', '36 Clifton Road'),
       ('a0000000-0000-0000-0000-000000000004', 'Karachi Festival Ground', 'Karachi', '48 University Road'),
       ('a0000000-0000-0000-0000-000000000005', 'Islamabad Theatre', 'Islamabad', '60 Jinnah Avenue'),
       ('a0000000-0000-0000-0000-000000000006', 'Islamabad Convention Hall', 'Islamabad', '72 Constitution Avenue')
ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name,
        city = EXCLUDED.city,
        address = EXCLUDED.address;

-- It generates 50,000 events starting from tomorrow and spread over the next year
-- Each event has a fixed ID. When this migration changes, its rows are updated instead of duplicated
WITH generated_events AS (
    SELECT event_number,
           NOW() + INTERVAL '1 day' + (event_number - 1) * INTERVAL '364 days' / 50000 AS starts_at
    FROM generate_series(1, 50000) AS events(event_number)
)
INSERT INTO event (
                      id,
                      venue_id,
                      organizer_id,
                      name,
                      description,
                      category,
                      starts_at,
                      ends_at,
                      base_price,
                      status
)
SELECT ('b0000000-0000-0000-0000-' || LPAD(event_number::TEXT, 12, '0'))::UUID,
       ('a0000000-0000-0000-0000-' || LPAD(((event_number - 1) % 6 + 1)::TEXT, 12, '0'))::UUID,
       'c0000000-0000-0000-0000-000000000001'::UUID,
       'Seed Event ' || event_number,
       'This event is generated for testing the catalog listing in development.',
       (ARRAY['CONCERT', 'FESTIVAL', 'SPORTS', 'THEATER', 'OTHER'])[(event_number - 1) % 5 + 1],
       starts_at,
       starts_at + INTERVAL '3 hours',
       (100 + (event_number - 1) % 1000 * 25)::NUMERIC(10, 2),
       -- Status uses a different cycle so every city and category has mostly published events
       CASE
           WHEN (event_number / 30) % 10 = 0 THEN 'DRAFT'
           WHEN (event_number / 30) % 10 = 1 THEN 'CANCELLED'
           ELSE 'PUBLISHED'
       END
FROM generated_events
ON CONFLICT (id) DO UPDATE
    SET venue_id = EXCLUDED.venue_id,
        organizer_id = EXCLUDED.organizer_id,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        category = EXCLUDED.category,
        starts_at = EXCLUDED.starts_at,
        ends_at = EXCLUDED.ends_at,
        base_price = EXCLUDED.base_price,
        status = EXCLUDED.status,
        updated_at = NOW();

-- It updates table statistics so EXPLAIN can use the seeded data immediately
ANALYZE venue;
ANALYZE event;
