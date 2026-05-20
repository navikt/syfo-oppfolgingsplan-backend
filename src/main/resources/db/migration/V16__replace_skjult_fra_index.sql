DROP INDEX idx_oppfolgingsplan_skjult_fra_null;

CREATE INDEX idx_oppfolgingsplan_visible_lookup
    ON oppfolgingsplan (sykmeldt_fnr, organisasjonsnummer, created_at DESC)
    WHERE skjult_fra IS NULL;
