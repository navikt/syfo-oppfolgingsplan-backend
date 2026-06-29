DROP TABLE IF EXISTS paaminnelse;
CREATE TABLE IF NOT EXISTS paaminnelse
(
    uuid                UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    organisasjonsnummer TEXT             NOT NULL,
    sykmeldt_fnr        TEXT             NOT NULL,
    bestilt             BOOLEAN          NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    outbox_at           TIMESTAMPTZ               DEFAULT NULL,
    UNIQUE (organisasjonsnummer, sykmeldt_fnr)
);
