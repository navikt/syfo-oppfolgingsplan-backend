ALTER TABLE oppfolgingsplan
    ALTER COLUMN event_id SET DEFAULT gen_random_uuid();

UPDATE oppfolgingsplan
SET event_id = gen_random_uuid()
WHERE event_id IS NULL
  -- NOW() uses the transaction start time, so include requests already in flight when V23 was installed.
  AND created_at >= (
      SELECT installed_on - INTERVAL '5 minutes'
      FROM flyway_schema_history
      WHERE version = '23'
  );

INSERT INTO outbox (uuid, message_type, dedup_key, external_ref, scheduled_at)
SELECT event_id,
       'OPPFOLGINGSPLAN_OPPRETTET',
       uuid::TEXT,
       uuid::TEXT,
       created_at
FROM oppfolgingsplan
WHERE event_id IS NOT NULL
  AND varsel_published_at IS NULL
ON CONFLICT DO NOTHING;
