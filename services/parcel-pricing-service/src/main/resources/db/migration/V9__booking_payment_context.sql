-- Flyway Migration V7: Payment Context, Booking Origin & Snapshot
-- Schema: parcel_pricing

ALTER TABLE parcel_pricing.parcels
    ADD COLUMN IF NOT EXISTS channel VARCHAR(30) NOT NULL DEFAULT 'DIRECT_WEBSITE',
    ADD COLUMN IF NOT EXISTS merchant_id UUID,
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN IF NOT EXISTS pricing_snapshot_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pricing_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_parcels_channel ON parcel_pricing.parcels(channel);
CREATE INDEX IF NOT EXISTS idx_parcels_merchant_id ON parcel_pricing.parcels(merchant_id);
CREATE INDEX IF NOT EXISTS idx_parcels_payment_method ON parcel_pricing.parcels(payment_method);
