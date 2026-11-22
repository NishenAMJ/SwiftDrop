-- Cash on Delivery, for merchant orders only. Copied from Parcel & Pricing when the parcel is assigned,
-- so the event Payment & Settlement receives carries the amounts that were agreed at booking time.
-- All null for parcels that are not merchant COD orders.
ALTER TABLE delivery_assignments ADD COLUMN merchant_id UUID;
ALTER TABLE delivery_assignments ADD COLUMN cod_amount NUMERIC(12, 2);
ALTER TABLE delivery_assignments ADD COLUMN pricing_snapshot_id VARCHAR(64);
ALTER TABLE delivery_assignments ADD COLUMN currency VARCHAR(3);

-- Who to meet and where, copied from Parcel & Pricing when the parcel is assigned. Shown only to the
-- assigned courier and dispatchers while the job is open.
ALTER TABLE delivery_assignments ADD COLUMN pickup_contact_name VARCHAR(100);
ALTER TABLE delivery_assignments ADD COLUMN pickup_contact_phone VARCHAR(25);
ALTER TABLE delivery_assignments ADD COLUMN pickup_address VARCHAR(400);
ALTER TABLE delivery_assignments ADD COLUMN delivery_contact_name VARCHAR(100);
ALTER TABLE delivery_assignments ADD COLUMN delivery_contact_phone VARCHAR(25);
ALTER TABLE delivery_assignments ADD COLUMN delivery_address VARCHAR(400);

-- The cash the courier actually collected, recorded with the proof of delivery.
ALTER TABLE proof_of_delivery ADD COLUMN collected_amount NUMERIC(12, 2);
