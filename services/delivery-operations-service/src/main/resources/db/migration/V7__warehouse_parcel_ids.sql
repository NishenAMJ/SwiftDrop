-- Parcel & Pricing's UUID is required by Tracking events. Historical warehouse rows predate the
-- lookup integration, so these columns remain nullable for those rows; every new arrival and
-- transfer stores a resolved parcel id in application code.
ALTER TABLE warehouse_parcels ADD COLUMN parcel_id UUID;
ALTER TABLE parcel_transfers ADD COLUMN parcel_id UUID;
