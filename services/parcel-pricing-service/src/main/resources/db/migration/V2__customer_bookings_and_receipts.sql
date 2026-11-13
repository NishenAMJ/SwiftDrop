-- Flyway Migration V2: Member 3 Customer Bookings, Addresses, Urgency Tags, Receipts & History
-- Schema: parcel_pricing

-- 1. Sequence for human-readable tracking references (e.g. SWD-2026-000001)
CREATE SEQUENCE IF NOT EXISTS parcel_pricing.parcel_ref_seq START WITH 1 INCREMENT BY 1;

-- 2. Add Member 3 columns to existing parcels table
ALTER TABLE parcel_pricing.parcels
    ADD COLUMN tracking_number VARCHAR(32) UNIQUE,
    ADD COLUMN description VARCHAR(255),
    ADD COLUMN weight_kg NUMERIC(6, 2),
    ADD COLUMN urgency VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN estimated_fee NUMERIC(10, 2),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- 3. Addresses Table for Pickup and Delivery
CREATE TABLE parcel_pricing.parcel_addresses (
    id BIGSERIAL PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcel_pricing.parcels(id) ON DELETE CASCADE,
    address_type VARCHAR(16) NOT NULL, -- 'PICKUP' or 'DELIVERY'
    contact_name VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(25) NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20),
    zone_code VARCHAR(50)
);

-- 4. Receipts Table
CREATE TABLE parcel_pricing.booking_receipts (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(32) NOT NULL UNIQUE,
    parcel_id UUID NOT NULL REFERENCES parcel_pricing.parcels(id) ON DELETE CASCADE,
    total_amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'LKR',
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_parcels_customer_id ON parcel_pricing.parcels(customer_id);
CREATE INDEX IF NOT EXISTS idx_parcels_tracking_number ON parcel_pricing.parcels(tracking_number);
CREATE INDEX IF NOT EXISTS idx_addresses_parcel_id ON parcel_pricing.parcel_addresses(parcel_id);
CREATE INDEX IF NOT EXISTS idx_receipts_parcel_id ON parcel_pricing.booking_receipts(parcel_id);

