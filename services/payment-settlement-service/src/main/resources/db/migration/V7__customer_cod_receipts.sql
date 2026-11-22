-- Customer delivery fees are not merchant proceeds and never enter the merchant ledger.
CREATE TABLE payment_settlement.customer_cod_receipts (
 id UUID PRIMARY KEY,
 event_id UUID NOT NULL UNIQUE,
 booking_id UUID NOT NULL UNIQUE,
 customer_id UUID NOT NULL,
 courier_id UUID NOT NULL,
 delivery_attempt_id UUID NOT NULL UNIQUE,
 tracking_number VARCHAR(40) NOT NULL,
 pricing_snapshot_id VARCHAR(64) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
 currency VARCHAR(3) NOT NULL,
 collected_at TIMESTAMPTZ NOT NULL,
 fingerprint TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
