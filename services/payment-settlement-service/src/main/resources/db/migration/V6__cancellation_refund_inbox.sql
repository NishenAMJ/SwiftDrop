CREATE TABLE payment_settlement.cancellation_inbox (
 event_id UUID PRIMARY KEY,
 booking_id UUID NOT NULL UNIQUE,
 payment_id UUID,
 fingerprint TEXT NOT NULL,
 payload TEXT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
   CHECK (status IN ('PENDING','COMPLETED','ATTENTION_REQUIRED')),
 refund_id UUID REFERENCES payment_settlement.payment_transactions(id),
 failure_reason VARCHAR(500),
 created_at TIMESTAMP NOT NULL DEFAULT timezone('UTC',now()),
 updated_at TIMESTAMP NOT NULL DEFAULT timezone('UTC',now()),
 next_attempt_at TIMESTAMP NOT NULL DEFAULT timezone('UTC',now())
);
CREATE UNIQUE INDEX uq_cancellation_payment ON payment_settlement.cancellation_inbox(payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX idx_cancellation_pending ON payment_settlement.cancellation_inbox(next_attempt_at) WHERE status='PENDING';
