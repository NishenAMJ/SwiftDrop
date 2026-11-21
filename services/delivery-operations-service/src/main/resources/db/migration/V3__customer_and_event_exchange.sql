-- The customer who booked the parcel, taken from Parcel & Pricing when the parcel is assigned.
-- Needed for parcel.status-changed, which notification-webhook-service uses to email the customer.
-- Null for bookings without a registered customer.
ALTER TABLE delivery_assignments ADD COLUMN customer_id UUID;

-- Delivery status events go to swiftdrop.delivery-operations (Tracking); customer status events go to
-- swiftdrop.delivery (Notification). Each outbox row now says where it is published.
ALTER TABLE outbox_events ADD COLUMN exchange VARCHAR(100) NOT NULL DEFAULT 'swiftdrop.delivery-operations';
