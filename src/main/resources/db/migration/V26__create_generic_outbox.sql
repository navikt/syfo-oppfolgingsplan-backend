CREATE TABLE outbox
(
    uuid                UUID PRIMARY KEY,
    message_type        TEXT        NOT NULL,
    dedup_key           TEXT        NOT NULL,
    external_ref        TEXT        NOT NULL,
    payload             JSONB       NOT NULL,
    available_at        TIMESTAMPTZ NOT NULL,
    status              TEXT        NOT NULL DEFAULT 'READY',
    claim_token         UUID,
    lease_until         TIMESTAMPTZ,
    failure_count       INTEGER     NOT NULL DEFAULT 0,
    last_failure_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ,
    cancellation_reason TEXT,
    CONSTRAINT uq_outbox_message UNIQUE (message_type, dedup_key),
    CONSTRAINT chk_outbox_status CHECK (status IN ('READY', 'CLAIMED', 'SENT', 'CANCELLED')),
    CONSTRAINT chk_outbox_failure_metadata CHECK (
        (failure_count = 0 AND last_failure_at IS NULL)
        OR (failure_count > 0 AND last_failure_at IS NOT NULL)
    ),
    CONSTRAINT chk_outbox_state_metadata CHECK (
        (status = 'READY' AND claim_token IS NULL AND lease_until IS NULL
            AND sent_at IS NULL AND cancellation_reason IS NULL)
        OR (status = 'CLAIMED' AND claim_token IS NOT NULL AND lease_until IS NOT NULL
            AND sent_at IS NULL AND cancellation_reason IS NULL)
        OR (status = 'SENT' AND claim_token IS NULL AND lease_until IS NULL
            AND sent_at IS NOT NULL AND cancellation_reason IS NULL)
        OR (status = 'CANCELLED' AND claim_token IS NULL AND lease_until IS NULL
            AND sent_at IS NULL AND cancellation_reason IS NOT NULL)
    )
);

CREATE INDEX idx_outbox_ready
    ON outbox (message_type, available_at, created_at, uuid)
    WHERE status = 'READY';

CREATE INDEX idx_outbox_expired_claim
    ON outbox (message_type, lease_until, created_at, uuid)
    WHERE status = 'CLAIMED';
