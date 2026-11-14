
CREATE TABLE payment_settlement.payments (

    id UUID PRIMARY KEY,

    booking_id VARCHAR(100) NOT NULL,

    amount NUMERIC(12, 2) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    status VARCHAR(20) NOT NULL,

    provider_reference VARCHAR(255),

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT chk_payment_amount
        CHECK (amount > 0),

    CONSTRAINT chk_payment_status
        CHECK (status IN (
            'PENDING',
            'SUCCESS',
            'FAILED',
            'REFUNDED'
        ))

);

CREATE INDEX idx_payments_booking_id
ON payment_settlement.payments (booking_id);
