-- A V25.1/V26/V27 pilot was briefly deployed to dev from an unmerged branch. Dev removes those
-- three Flyway history rows before deploying this migration. Refuse to delete anything unless an
-- existing table is empty and has the exact notification-outbox signature left by that pilot.
DO
$$
BEGIN
    IF to_regclass('outbox') IS NOT NULL THEN
        IF NOT (
            SELECT array_agg(column_name::TEXT ORDER BY column_name) = ARRAY[
                'attempt_count',
                'created_at',
                'dedup_key',
                'external_ref',
                'last_attempt_at',
                'message_type',
                'payload',
                'scheduled_at',
                'sent_at',
                'status',
                'uuid'
            ]::TEXT[]
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'outbox'
        ) THEN
            RAISE EXCEPTION 'Existing outbox table does not match the abandoned notification pilot schema';
        END IF;

        IF EXISTS (SELECT 1 FROM outbox) THEN
            RAISE EXCEPTION 'Abandoned notification pilot outbox is not empty; inspect its messages before cleanup';
        END IF;

        DROP TABLE outbox;
    END IF;
END
$$;

-- V27 added this default. Current application code owns event-id creation explicitly.
ALTER TABLE oppfolgingsplan
    ALTER COLUMN event_id DROP DEFAULT;
