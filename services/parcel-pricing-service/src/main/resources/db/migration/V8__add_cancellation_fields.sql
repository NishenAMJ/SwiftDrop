-- Migration to add cancellation related columns to parcels table
-- This follows existing Flyway naming conventions

ALTER TABLE parcel_pricing.parcels
    ADD COLUMN cancellation_fee NUMERIC(10, 2),
    ADD COLUMN refund_amount NUMERIC(10, 2),
    ADD COLUMN cancellation_status VARCHAR(30),
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancellation_transaction_ref VARCHAR(64),
    ADD COLUMN payment_status VARCHAR(30);
