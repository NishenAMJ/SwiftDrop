-- Migration to add cancellation related columns to parcels table
-- This follows existing Flyway naming conventions

ALTER TABLE parcel_pricing.parcels
    ADD COLUMN IF NOT EXISTS cancellation_fee NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS cancellation_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS cancellation_transaction_ref VARCHAR(64),
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30);
