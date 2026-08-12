-- Recovery for an abandoned dev-only V26 migration that was deployed before this branch was rebased.
-- Fail instead of dropping anything if an existing outbox table does not have that migration's signature.
DO
$$
BEGIN
    IF to_regclass('outbox') IS NOT NULL THEN
        IF NOT (
            EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'outbox'
                  AND column_name = 'sendt_at'
            )
            AND NOT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'outbox'
                  AND column_name = 'sent_at'
            )
            AND NOT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'outbox'
                  AND column_name = 'attempt_count'
            )
        ) THEN
            RAISE EXCEPTION 'Existing outbox table does not match the abandoned pilot schema';
        END IF;

        DROP TABLE outbox;
    END IF;
END
$$;

-- This column was introduced by the same abandoned migration and is not used by the current application.
ALTER TABLE paaminnelse
    DROP COLUMN IF EXISTS forlop_fom;
