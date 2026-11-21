-- V11: booking lifecycle, real pricing breakdown, customer snapshot, event outbox and inbox.
-- Schema: parcel_pricing

-- 1. Fee breakdown copied from PricingService when the booking is created.
--    The receipt shows these values, so later pricing rule edits never change an issued price.
ALTER TABLE parcel_pricing.parcels
    ADD COLUMN IF NOT EXISTS base_fee NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS weight_charge NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS zone_charge NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS urgent_surcharge NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS pricing_rule_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pricing_zone_code VARCHAR(50);

-- 2. Who booked the parcel, as Identity knew them at booking time (for the receipt and payer details).
ALTER TABLE parcel_pricing.parcels
    ADD COLUMN IF NOT EXISTS customer_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS customer_email VARCHAR(150),
    ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(25);

-- 3. Payment link and lifecycle timestamps.
ALTER TABLE parcel_pricing.parcels
    ADD COLUMN IF NOT EXISTS payment_id UUID,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_parcels_status ON parcel_pricing.parcels(status);
CREATE INDEX IF NOT EXISTS idx_parcels_pricing_expires_at ON parcel_pricing.parcels(pricing_expires_at);

-- 4. Transactional outbox. A row is written in the same transaction as the booking change and
--    a background worker publishes it to RabbitMQ, so an event is never lost or sent for a rollback.
CREATE TABLE IF NOT EXISTS parcel_pricing.event_outbox (
    id UUID PRIMARY KEY,
    exchange_name VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_pending
    ON parcel_pricing.event_outbox(next_attempt_at)
    WHERE published_at IS NULL;

-- 5. Inbox of consumed event IDs so a redelivered RabbitMQ message has no second effect.
CREATE TABLE IF NOT EXISTS parcel_pricing.processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
