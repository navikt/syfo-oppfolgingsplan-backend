CREATE TABLE foresporsel_oppfolgingsplan
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sykmeldt_fnr        VARCHAR(11)                NOT NULL,
    narmeste_leder_fnr  VARCHAR(11)                NOT NULL,
    organisasjonsnummer VARCHAR(9)                 NOT NULL,
    created_at          TIMESTAMPTZ      DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_foresporsel_sykmeldt_nl
    ON foresporsel_oppfolgingsplan (sykmeldt_fnr, narmeste_leder_fnr);

GRANT SELECT ON foresporsel_oppfolgingsplan TO "esyfo-analyse";
