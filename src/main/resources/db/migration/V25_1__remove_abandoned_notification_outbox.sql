-- Temporary dev-only cleanup. Do not merge this migration to main.
--
-- Before deploying this branch:
-- 1. Scale every app replica to zero.
-- 2. Verify and delete only the abandoned pilot history rows 25.1, 26 and 27.
-- 3. Verify that old pilot writers are stopped.
--
-- This branch is deployed only to the disposable dev environment. After the exact pilot signature
-- is verified, the table is dropped even when it contains rows. The final outbox PR contains only
-- a create-only V26 after this migration has run and the temporary V25.1 history row has been
-- removed again.
DO
$$
DECLARE
    deleted_row_count BIGINT;
    deleted_status_counts JSONB;
BEGIN
    IF to_regclass('public.outbox') IS NOT NULL THEN
        LOCK TABLE public.outbox IN ACCESS EXCLUSIVE MODE;

        IF NOT (
            SELECT array_agg(
                (column_name || ':' || udt_name || ':' || is_nullable)::TEXT
                ORDER BY column_name
            ) = ARRAY[
                'attempt_count:int4:NO',
                'created_at:timestamptz:NO',
                'dedup_key:text:NO',
                'external_ref:text:NO',
                'last_attempt_at:timestamptz:YES',
                'message_type:text:NO',
                'payload:jsonb:NO',
                'scheduled_at:timestamptz:NO',
                'sent_at:timestamptz:YES',
                'status:text:NO',
                'uuid:uuid:NO'
            ]::TEXT[]
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'outbox'
        ) THEN
            RAISE EXCEPTION 'Existing outbox table does not match the abandoned notification pilot schema';
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint constraint_definition
            JOIN pg_class table_definition
              ON table_definition.oid = constraint_definition.conrelid
            JOIN pg_namespace table_namespace
              ON table_namespace.oid = table_definition.relnamespace
            WHERE table_namespace.nspname = 'public'
              AND table_definition.relname = 'outbox'
              AND constraint_definition.conname = 'chk_outbox_status'
              AND constraint_definition.contype = 'c'
              AND pg_get_constraintdef(constraint_definition.oid) LIKE '%IRRELEVANT%'
              AND pg_get_constraintdef(constraint_definition.oid) LIKE '%FAILED%'
        ) THEN
            RAISE EXCEPTION 'Existing outbox table does not have the abandoned pilot status constraint';
        END IF;

        SELECT count(*)
        INTO deleted_row_count
        FROM public.outbox;

        SELECT jsonb_object_agg(status, row_count)
        INTO deleted_status_counts
        FROM (
            SELECT status, count(*) AS row_count
            FROM public.outbox
            GROUP BY status
        ) counts_by_status;

        DROP TABLE public.outbox;

        RAISE WARNING 'Deleted % abandoned dev outbox pilot rows; status counts=%',
            deleted_row_count,
            coalesce(deleted_status_counts, '{}'::jsonb);
    END IF;
END
$$;

-- The abandoned V27 added this default. Current application code owns event-id creation.
ALTER TABLE oppfolgingsplan
    ALTER COLUMN event_id DROP DEFAULT;
