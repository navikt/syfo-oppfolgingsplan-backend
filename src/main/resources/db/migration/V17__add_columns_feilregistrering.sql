ALTER TABLE oppfolgingsplan
    ADD COLUMN feilregistrert TIMESTAMPTZ,
    ADD COLUMN feilregistrert_aarsak TEXT;
