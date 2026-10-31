-- Transactional outbox: events are written in the same transaction as the change
-- they describe, then published to RabbitMQ (swiftdrop.identity) by a separate
-- publisher. Rows with published_at IS NULL are still waiting to be sent.
CREATE TABLE identity_merchant.outbox_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished
    ON identity_merchant.outbox_events (occurred_at)
    WHERE published_at IS NULL;
