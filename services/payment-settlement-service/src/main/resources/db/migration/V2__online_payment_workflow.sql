-- V1 is immutable. Existing rows remain readable to dispatchers only until ownership is known.
ALTER TABLE payment_settlement.payments DROP CONSTRAINT chk_payment_status;
ALTER TABLE payment_settlement.payments ADD CONSTRAINT chk_payment_status
 CHECK (status IN ('PENDING','SUCCESS','FAILED','PARTIALLY_REFUNDED','REFUNDED','CHARGEBACKED'));
ALTER TABLE payment_settlement.payments
 ADD COLUMN customer_id UUID,
 ADD COLUMN merchant_id UUID,
 ADD COLUMN initiated_by UUID,
 ADD COLUMN idempotency_key VARCHAR(128),
 ADD COLUMN pricing_snapshot_id UUID,
 ADD COLUMN booking_channel VARCHAR(30),
 ADD COLUMN payer_json TEXT,
 ADD COLUMN refunded_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
 ADD COLUMN reserved_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN reconcile_after TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ADD CONSTRAINT chk_refund_balance CHECK (refunded_amount >= 0 AND reserved_amount >= 0
   AND refunded_amount + reserved_amount <= amount),
 ADD CONSTRAINT uq_payment_request UNIQUE (initiated_by, idempotency_key);
UPDATE payment_settlement.payments SET refunded_amount=amount WHERE status='REFUNDED';
CREATE INDEX idx_payment_customer ON payment_settlement.payments(customer_id, created_at DESC);
CREATE INDEX idx_payment_merchant ON payment_settlement.payments(merchant_id, created_at DESC);
CREATE UNIQUE INDEX uq_payment_provider_reference ON payment_settlement.payments(provider_reference)
 WHERE provider_reference IS NOT NULL;

-- Serialize creation per booking without imposing constraints on legacy V1 rows.
CREATE TABLE payment_settlement.payment_booking_claims (
 booking_id VARCHAR(100) PRIMARY KEY,
 payment_id UUID NOT NULL UNIQUE REFERENCES payment_settlement.payments(id)
);

CREATE TABLE payment_settlement.payment_transactions (
 id UUID PRIMARY KEY,
 payment_id UUID NOT NULL REFERENCES payment_settlement.payments(id),
 actor_id UUID NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount > 0),
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','SUCCESS','FAILED')),
 provider_reference VARCHAR(255),
 submitted_at TIMESTAMP,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 UNIQUE(actor_id, idempotency_key)
);
CREATE INDEX idx_refund_payment ON payment_settlement.payment_transactions(payment_id);
CREATE TABLE payment_settlement.payment_callbacks (
 id UUID PRIMARY KEY,
 fingerprint VARCHAR(64) NOT NULL UNIQUE,
 payment_id UUID NOT NULL REFERENCES payment_settlement.payments(id),
 provider_reference VARCHAR(255),
 outcome VARCHAR(40) NOT NULL,
 received_at TIMESTAMP NOT NULL
);
CREATE TABLE payment_settlement.payment_outbox (
 id UUID PRIMARY KEY,
 event_type VARCHAR(60) NOT NULL,
 payload TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL,
 published_at TIMESTAMP,
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP NOT NULL,
 locked_until TIMESTAMP,
 lock_token UUID
);
CREATE INDEX idx_outbox_pending ON payment_settlement.payment_outbox(next_attempt_at)
 WHERE published_at IS NULL;
