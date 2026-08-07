ALTER TABLE paaminnelse
    ADD COLUMN forlop_fom DATE NOT NULL;

CREATE TABLE outbox
(
    uuid         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type TEXT        NOT NULL,
    dedup_key    TEXT        NOT NULL,
    external_ref TEXT        NOT NULL,
    payload      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'KLAR',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sendt_at     TIMESTAMPTZ,
    CONSTRAINT uq_outbox_dedup UNIQUE (message_type, dedup_key),
    CONSTRAINT chk_outbox_status CHECK (status IN ('KLAR', 'SENDT', 'IKKE_RELEVANT'))
);

CREATE INDEX idx_outbox_klar
    ON outbox (scheduled_at)
    WHERE status = 'KLAR';
