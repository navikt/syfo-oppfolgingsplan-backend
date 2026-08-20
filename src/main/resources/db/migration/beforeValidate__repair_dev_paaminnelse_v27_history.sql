-- Temporary dev-only recovery callback.
--
-- The V27 schema change was applied in dev before its Flyway history row was removed.
-- Restore only the canonical history entry when the schema proves that V27 is already
-- fully applied. This branch must never be merged.
DO
$$
DECLARE
    v_has_sykmeldingsperiode_id BOOLEAN;
    v_has_outbox_at             BOOLEAN;
BEGIN
    IF to_regclass('public.paaminnelse') IS NULL
        OR to_regclass('public.flyway_schema_history') IS NULL THEN
        RETURN;
    END IF;

    -- Serialize concurrent pod starts before reading or repairing Flyway history.
    PERFORM pg_advisory_xact_lock(hashtext('repair-dev-paaminnelse-v27-history'));

    IF EXISTS (
        SELECT 1
        FROM flyway_schema_history
        WHERE version = '27'
          AND success
    ) THEN
        RETURN;
    END IF;

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

    IF NOT v_has_sykmeldingsperiode_id OR v_has_outbox_at THEN
        RAISE EXCEPTION
            'Refusing temporary V27 history repair: paaminnelse does not match the complete V27 schema';
    END IF;

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
END
$$;
