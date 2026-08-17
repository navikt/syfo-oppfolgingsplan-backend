ALTER TABLE paaminnelse
    ADD COLUMN sykmeldingsperiode_id UUID NOT NULL REFERENCES sykmeldingsperiode(id);

ALTER TABLE paaminnelse
    DROP COLUMN outbox_at;
