-- V12: merchant orders become ordinary parcels.
-- Schema: parcel_pricing
--
-- A merchant order is a row in parcels with channel = 'MERCHANT_API' and merchant_id set (the
-- merchant_id claim from Identity, a UUID). It gets the same SWD tracking number, pricing, payment,
-- receipt, tracking events and status updates as a customer booking.

-- 1. Merchant-only fields on parcels.
ALTER TABLE parcel_pricing.parcels
    ADD COLUMN IF NOT EXISTS merchant_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cod_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS urgent_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS order_source VARCHAR(20);

ALTER TABLE parcel_pricing.parcels
    ADD CONSTRAINT ck_parcels_order_source
        CHECK (order_source IS NULL OR order_source IN ('DASHBOARD', 'EXTERNAL_API')),
    ADD CONSTRAINT ck_parcels_cod_amount
        CHECK (cod_amount IS NULL OR cod_amount > 0);

-- The merchant's own order number is unique per merchant (duplicate protection for the external API).
CREATE UNIQUE INDEX IF NOT EXISTS uq_parcels_merchant_reference
    ON parcel_pricing.parcels (merchant_id, merchant_reference)
    WHERE merchant_reference IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_parcels_merchant_created
    ON parcel_pricing.parcels (merchant_id, created_at DESC)
    WHERE merchant_id IS NOT NULL;

-- 2. The old separate table is no longer used. It only ever held dev-profile test data, keyed by a
--    BIGINT merchant id that does not exist in Identity (which uses UUIDs), so the rows cannot be
--    migrated. The table is kept under a new name instead of being dropped.
ALTER TABLE IF EXISTS parcel_pricing.merchant_orders RENAME TO merchant_orders_legacy;

-- 3. Urgent bookings pay the urgent surcharge on the seeded default rule (LKR 200).
--    Dispatchers can still turn it off per rule in Pricing Rules.
UPDATE parcel_pricing.pricing_rules
SET urgent_surcharge_enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE rule_code = 'STANDARD_DEFAULT'
  AND urgent_surcharge_enabled = FALSE;
