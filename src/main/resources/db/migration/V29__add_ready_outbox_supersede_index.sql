-- flyway:executeInTransaction=false
DROP INDEX CONCURRENTLY IF EXISTS idx_outbox_ready_message_type_external_ref;

CREATE INDEX CONCURRENTLY idx_outbox_ready_message_type_external_ref
    ON outbox (message_type, external_ref)
    WHERE status = 'READY';
