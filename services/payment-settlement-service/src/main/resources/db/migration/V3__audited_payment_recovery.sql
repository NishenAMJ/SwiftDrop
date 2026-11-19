-- Keep recovery decisions and released booking claims auditable without deleting history.
ALTER TABLE payment_settlement.payment_booking_claims
    ADD COLUMN released_at TIMESTAMP;

ALTER TABLE payment_settlement.payment_callbacks
    ADD COLUMN status_code SMALLINT;

CREATE TABLE payment_settlement.payment_recovery_audit (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_settlement.payments(id),
    refund_id UUID REFERENCES payment_settlement.payment_transactions(id),
    action_type VARCHAR(40) NOT NULL CHECK (action_type IN (
        'REFUND_CONFIRMED_SUCCESS', 'REFUND_CONFIRMED_FAILURE', 'BOOKING_CLAIM_RELEASED'
    )),
    prior_status VARCHAR(30) NOT NULL,
    resulting_status VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    evidence_type VARCHAR(40) NOT NULL,
    evidence_reference VARCHAR(200) NOT NULL,
    provider_reference VARCHAR(30),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_payment_recovery_audit_payment
    ON payment_settlement.payment_recovery_audit(payment_id, created_at DESC);
CREATE INDEX idx_payment_recovery_audit_refund
    ON payment_settlement.payment_recovery_audit(refund_id, created_at DESC)
    WHERE refund_id IS NOT NULL;
