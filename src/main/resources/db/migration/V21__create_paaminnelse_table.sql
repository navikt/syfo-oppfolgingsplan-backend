CREATE TABLE paaminnelse (
    organisasjonsnummer TEXT NOT NULL,
    sykmeldt_fnr TEXT NOT NULL,
    bestilt BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sykmeldt_fnr, organisasjonsnummer)
);
