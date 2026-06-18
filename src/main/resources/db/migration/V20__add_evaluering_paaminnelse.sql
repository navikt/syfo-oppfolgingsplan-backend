ALTER TABLE oppfolgingsplan
    ADD COLUMN evaluering_paaminnelse BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN evaluering_paaminnelse_outbox_at TIMESTAMPTZ;
