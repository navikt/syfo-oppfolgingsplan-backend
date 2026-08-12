CREATE TABLE outbox
(
    uuid            UUID PRIMARY KEY,
    message_type    TEXT        NOT NULL,
    dedup_key       TEXT        NOT NULL,
    external_ref    TEXT        NOT NULL,
    payload         JSONB       NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'READY',
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    CONSTRAINT uq_outbox_message UNIQUE (message_type, dedup_key),
    CONSTRAINT chk_outbox_status CHECK (status IN ('READY', 'SENT', 'IRRELEVANT', 'FAILED')),
    CONSTRAINT chk_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_ready
    ON outbox (message_type, scheduled_at)
    WHERE status = 'READY';
