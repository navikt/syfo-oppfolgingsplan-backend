ALTER TABLE oppfolgingsplan
    ADD COLUMN skjult_fra TIMESTAMPTZ;

CREATE INDEX idx_oppfolgingsplan_skjult_fra_null ON oppfolgingsplan (uuid) WHERE skjult_fra IS NULL;
