CREATE TABLE unntaksvurdering
(
    uuid                     UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    sykmeldt_fnr             TEXT         NOT NULL,
    organisasjonsnummer      TEXT         NOT NULL,
    narmeste_leder_fnr       TEXT         NOT NULL,
    narmeste_leder_full_name TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    skjult_fra               TIMESTAMPTZ
);

CREATE INDEX idx_unntaksvurdering_lookup ON unntaksvurdering (sykmeldt_fnr, organisasjonsnummer);
CREATE INDEX idx_unntaksvurdering_skjult_fra_null ON unntaksvurdering (uuid) WHERE skjult_fra IS NULL;

GRANT SELECT ON unntaksvurdering TO "esyfo-analyse";