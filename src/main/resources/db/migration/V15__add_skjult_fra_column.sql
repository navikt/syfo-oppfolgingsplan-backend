ALTER TABLE oppfolgingsplan
    ADD COLUMN skjult_fra TIMESTAMPTZ;

CREATE INDEX idx_oppfolgingsplan_visible_lookup
    ON oppfolgingsplan (sykmeldt_fnr, organisasjonsnummer, created_at DESC)
    WHERE skjult_fra IS NULL;
