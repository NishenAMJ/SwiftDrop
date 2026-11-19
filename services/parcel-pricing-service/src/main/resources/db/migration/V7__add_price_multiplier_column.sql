-- Flyway Migration V7: Add price_multiplier column to parcel_price table
-- Schema: parcel_pricing

ALTER TABLE parcel_pricing.parcels
    ADD COLUMN price_multiplier NUMERIC(5,2) DEFAULT 1.0;
