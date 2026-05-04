CREATE TABLE sykmeldingsperiode
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sykmeldt_fnr        VARCHAR(11)                NOT NULL,
    organisasjonsnummer VARCHAR(9)                 NOT NULL,
    sykmelding_id       VARCHAR(64)                NOT NULL,
    fom                 DATE                       NOT NULL,
    tom                 DATE                       NOT NULL,
    invalidated_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ      DEFAULT NOW() NOT NULL
);

CREATE UNIQUE INDEX idx_sykmeldingsperiode_idempotent
    ON sykmeldingsperiode (sykmelding_id, fom, tom);

CREATE INDEX idx_sykmeldingsperiode_lookup
    ON sykmeldingsperiode (sykmeldt_fnr, organisasjonsnummer, tom);

GRANT SELECT ON sykmeldingsperiode TO "esyfo-analyse";
