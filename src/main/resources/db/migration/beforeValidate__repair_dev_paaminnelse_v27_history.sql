-- Temporary dev-only recovery callback.
--
-- The V27 and V28 schema changes were applied in dev before their Flyway history rows
-- were removed. Restore only the canonical history entries when the schema proves that
-- each migration is already fully applied. This branch must never be merged.
DO
$$
DECLARE
    v_has_sykmeldingsperiode_id BOOLEAN;
    v_has_outbox_at             BOOLEAN;
    v_has_organisasjonsnavn     BOOLEAN;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RETURN;
    END IF;

    -- Serialize concurrent pod starts before reading or repairing Flyway history.
    PERFORM pg_advisory_xact_lock(hashtext('repair-dev-v27-v28-history'));

    IF NOT EXISTS (
        SELECT 1
        FROM flyway_schema_history
        WHERE version = '27'
          AND success
    ) AND to_regclass('public.paaminnelse') IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'paaminnelse'
              AND column_name = 'sykmeldingsperiode_id'
        )
        INTO v_has_sykmeldingsperiode_id;

        SELECT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'paaminnelse'
              AND column_name = 'outbox_at'
        )
        INTO v_has_outbox_at;

        IF v_has_sykmeldingsperiode_id AND NOT v_has_outbox_at THEN
            INSERT INTO flyway_schema_history (
                installed_rank,
                version,
                description,
                type,
                script,
                checksum,
                installed_by,
                installed_on,
                execution_time,
                success
            )
            SELECT COALESCE(MAX(installed_rank), 0) + 1,
                   '27',
                   'update paaminnelse table',
                   'SQL',
                   'V27__update_paaminnelse_table.sql',
                   -836683551,
                   current_user,
                   NOW(),
                   0,
                   TRUE
            FROM flyway_schema_history;
        ELSIF v_has_sykmeldingsperiode_id OR NOT v_has_outbox_at THEN
            RAISE EXCEPTION
                'Refusing temporary V27 history repair: paaminnelse has a partial V27 schema';
        END IF;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM flyway_schema_history
        WHERE version = '28'
          AND success
    ) AND to_regclass('public.unntaksvurdering') IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'unntaksvurdering'
              AND column_name = 'organisasjonsnavn'
              AND data_type = 'text'
              AND is_nullable = 'YES'
        )
        INTO v_has_organisasjonsnavn;

        IF v_has_organisasjonsnavn THEN
            INSERT INTO flyway_schema_history (
                installed_rank,
                version,
                description,
                type,
                script,
                checksum,
                installed_by,
                installed_on,
                execution_time,
                success
            )
            SELECT COALESCE(MAX(installed_rank), 0) + 1,
                   '28',
                   'add organisasjonsnavn to unntaksvurdering',
                   'SQL',
                   'V28__add_organisasjonsnavn_to_unntaksvurdering.sql',
                   954084108,
                   current_user,
                   NOW(),
                   0,
                   TRUE
            FROM flyway_schema_history;
        END IF;
    END IF;
END
$$;
