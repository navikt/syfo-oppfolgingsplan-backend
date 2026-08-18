-- Midlertidig dev-opprydding. Skal ikke merges til main.
--
-- Dev fikk V26__create_generic_outbox.sql deployet 2026-08-13 fra en tidligere versjon av #408.
-- Migrasjonsfilen ble endret etterpå (sent_at -> completed_at, ny state-constraint, to nye
-- indekser), så Flyway-checksummen i dev matcher ikke lenger filen på PR-branchen, og appen
-- kraesjer under validate ved oppstart.
--
-- Dette er en afterMigrate-callback, ikke en versjonert migrasjon. Callbacks foerer ingen rad i
-- flyway_schema_history, saa denne branchen etterlater ingen historikk som maa ryddes manuelt
-- etterpaa. Callbacken kjoerer ved hver oppstart og er derfor idempotent: den gjoer ingenting med
-- mindre den nøyaktige forlatte piloten finnes.
--
-- Denne branchen er basert paa main, som ikke har V26. Dev-raden 26 er dermed en "future
-- migration" for denne branchen og ignoreres av validate, slik at oppstart gaar gjennom.
--
-- Bruk: deploy denne branchen til dev, verifiser at appen starter, deploy saa #408. Lukk denne
-- PR-en uten aa merge.
DO
$$
DECLARE
    deleted_history_rows BIGINT;
BEGIN
    IF to_regclass('public.outbox') IS NOT NULL THEN
        LOCK TABLE public.outbox IN ACCESS EXCLUSIVE MODE;

        -- Den forlatte varianten har sent_at. Den gjeldende V26 har completed_at i stedet.
        -- Uten dette skillet kunne callbacken droppe en korrekt opprettet tabell.
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'outbox'
              AND column_name = 'sent_at'
        ) THEN
            RAISE NOTICE 'outbox matcher ikke den forlatte piloten; lar tabellen staa';
            RETURN;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'outbox'
              AND column_name = 'completed_at'
        ) THEN
            RAISE NOTICE 'outbox har allerede completed_at; lar tabellen staa';
            RETURN;
        END IF;

        DROP TABLE public.outbox;
        RAISE WARNING 'Droppet forlatt dev-outbox fra tidligere versjon av #408';
    END IF;

    -- Treffer bare den nøyaktige raden fra 2026-08-13-deployen (checksum for commit 9d95e2f).
    DELETE FROM flyway_schema_history
    WHERE version = '26'
      AND script = 'V26__create_generic_outbox.sql'
      AND checksum = -380080342;

    GET DIAGNOSTICS deleted_history_rows = ROW_COUNT;

    IF deleted_history_rows > 0 THEN
        RAISE WARNING 'Fjernet % flyway-historikkrad(er) for den forlatte V26', deleted_history_rows;
    END IF;
END
$$;
