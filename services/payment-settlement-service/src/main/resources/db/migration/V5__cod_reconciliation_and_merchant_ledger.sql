-- Member 9: COD collection, courier cash reconciliation and merchant settlement.
-- Posted ledger rows are append-only; corrections use new ADJUSTMENT entries.

CREATE TABLE payment_settlement.cod_collections (
    id UUID PRIMARY KEY,
    booking_id VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(40) NOT NULL,
    pricing_snapshot_id VARCHAR(64) NOT NULL,
    merchant_id UUID NOT NULL,
    courier_id UUID NOT NULL,
    delivery_attempt_id UUID NOT NULL,
    expected_amount NUMERIC(12, 2) NOT NULL CHECK (expected_amount > 0),
    collected_amount NUMERIC(12, 2) NOT NULL CHECK (collected_amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    collected_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECORDED', 'IN_HANDOVER', 'RECONCILED')),
    source_event_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cod_booking UNIQUE (booking_id),
    CONSTRAINT uq_cod_delivery_attempt UNIQUE (delivery_attempt_id)
);

CREATE INDEX idx_cod_courier_status
    ON payment_settlement.cod_collections(courier_id, status, collected_at);
CREATE INDEX idx_cod_merchant
    ON payment_settlement.cod_collections(merchant_id, collected_at DESC);

CREATE TABLE payment_settlement.cash_handovers (
    id UUID PRIMARY KEY,
    courier_id UUID NOT NULL,
    expected_amount NUMERIC(12, 2) NOT NULL CHECK (expected_amount > 0),
    submitted_amount NUMERIC(12, 2) NOT NULL CHECK (submitted_amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'FLAGGED')),
    idempotency_key VARCHAR(128) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_handover_request UNIQUE (courier_id, idempotency_key)
);

CREATE INDEX idx_handover_courier
    ON payment_settlement.cash_handovers(courier_id, submitted_at DESC);
CREATE INDEX idx_handover_status
    ON payment_settlement.cash_handovers(status, submitted_at);

CREATE TABLE payment_settlement.cash_handover_items (
    handover_id UUID NOT NULL REFERENCES payment_settlement.cash_handovers(id),
    cod_collection_id UUID NOT NULL UNIQUE REFERENCES payment_settlement.cod_collections(id),
    PRIMARY KEY (handover_id, cod_collection_id)
);

CREATE TABLE payment_settlement.cash_reconciliations (
    id UUID PRIMARY KEY,
    handover_id UUID NOT NULL UNIQUE REFERENCES payment_settlement.cash_handovers(id),
    expected_amount NUMERIC(12, 2) NOT NULL CHECK (expected_amount > 0),
    received_amount NUMERIC(12, 2) NOT NULL CHECK (received_amount >= 0),
    difference_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('EXACT', 'SHORT', 'EXCESS')),
    investigation_note VARCHAR(500),
    confirmed_by UUID NOT NULL,
    confirmation_idempotency_key VARCHAR(128) NOT NULL,
    confirmed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_reconciliation_request UNIQUE (confirmed_by, confirmation_idempotency_key),
    CONSTRAINT chk_reconciliation_difference CHECK (difference_amount = received_amount - expected_amount),
    CONSTRAINT chk_reconciliation_note CHECK (status = 'EXACT' OR investigation_note IS NOT NULL)
);

CREATE TABLE payment_settlement.merchant_ledger_entries (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount <> 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    entry_type VARCHAR(30) NOT NULL CHECK (entry_type IN (
        'COD_COLLECTION', 'DELIVERY_CHARGE', 'ADJUSTMENT', 'SETTLEMENT'
    )),
    source_reference VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    reason VARCHAR(500),
    settlement_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_ledger_source UNIQUE (entry_type, source_reference),
    CONSTRAINT chk_adjustment_reason CHECK (entry_type <> 'ADJUSTMENT' OR reason IS NOT NULL)
);

CREATE INDEX idx_ledger_merchant
    ON payment_settlement.merchant_ledger_entries(merchant_id, created_at DESC);

CREATE TABLE payment_settlement.merchant_settlements (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    cutoff_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CALCULATED', 'APPROVED', 'PAID')),
    idempotency_key VARCHAR(128) NOT NULL,
    calculated_by UUID NOT NULL,
    calculated_at TIMESTAMP NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMP,
    approval_note VARCHAR(500),
    paid_by UUID,
    paid_at TIMESTAMP,
    external_payment_reference VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_settlement_request UNIQUE (calculated_by, idempotency_key),
    CONSTRAINT chk_settlement_approval CHECK (
        (status = 'CALCULATED' AND approved_by IS NULL AND approved_at IS NULL)
        OR (status IN ('APPROVED', 'PAID') AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT chk_settlement_payment CHECK (
        (status <> 'PAID' AND paid_by IS NULL AND paid_at IS NULL AND external_payment_reference IS NULL)
        OR (status = 'PAID' AND paid_by IS NOT NULL AND paid_at IS NOT NULL AND external_payment_reference IS NOT NULL)
    )
);

CREATE INDEX idx_settlement_merchant
    ON payment_settlement.merchant_settlements(merchant_id, calculated_at DESC);

CREATE TABLE payment_settlement.merchant_settlement_entries (
    settlement_id UUID NOT NULL REFERENCES payment_settlement.merchant_settlements(id),
    ledger_entry_id UUID NOT NULL UNIQUE REFERENCES payment_settlement.merchant_ledger_entries(id),
    PRIMARY KEY (settlement_id, ledger_entry_id)
);

ALTER TABLE payment_settlement.merchant_ledger_entries
    ADD COLUMN settlement_id UUID REFERENCES payment_settlement.merchant_settlements(id);

CREATE UNIQUE INDEX uq_settlement_ledger_entry
    ON payment_settlement.merchant_ledger_entries(settlement_id)
    WHERE entry_type = 'SETTLEMENT';

CREATE FUNCTION payment_settlement.protect_posted_merchant_ledger_entry()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'posted merchant ledger entries cannot be deleted';
    END IF;

    IF ROW(
        OLD.id, OLD.merchant_id, OLD.amount, OLD.currency, OLD.entry_type,
        OLD.source_reference, OLD.idempotency_key, OLD.reason, OLD.created_at
    ) IS DISTINCT FROM ROW(
        NEW.id, NEW.merchant_id, NEW.amount, NEW.currency, NEW.entry_type,
        NEW.source_reference, NEW.idempotency_key, NEW.reason, NEW.created_at
    ) THEN
        RAISE EXCEPTION 'posted merchant ledger financial fields are immutable';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_protect_posted_merchant_ledger_entry
BEFORE UPDATE OR DELETE ON payment_settlement.merchant_ledger_entries
FOR EACH ROW EXECUTE FUNCTION payment_settlement.protect_posted_merchant_ledger_entry();
