-- Merchant and courier applications. Both follow the same status flow
-- (PENDING -> APPROVED/REJECTED, APPROVED <-> SUSPENDED, REJECTED -> PENDING on resubmit),
-- and keep their latest dispatcher decision on the row for quick display.

CREATE TABLE identity_merchant.merchants (
    merchant_id                  UUID PRIMARY KEY,
    user_id                      UUID         NOT NULL REFERENCES identity_merchant.users (user_id),
    business_name                VARCHAR(120) NOT NULL,
    business_type                VARCHAR(30)  NOT NULL,
    business_registration_number VARCHAR(50),
    contact_email                VARCHAR(254),
    contact_phone                VARCHAR(16)  NOT NULL,
    address_line1                VARCHAR(120) NOT NULL,
    address_line2                VARCHAR(120),
    city                         VARCHAR(80)  NOT NULL,
    postal_code                  VARCHAR(20),
    status                       VARCHAR(20)  NOT NULL,
    last_decision_status         VARCHAR(20),
    last_decision_reason         VARCHAR(500),
    last_decided_at              TIMESTAMPTZ,
    last_decided_by              UUID,
    submitted_at                 TIMESTAMPTZ  NOT NULL,
    created_at                   TIMESTAMPTZ  NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL,
    version                      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_merchants_user_id UNIQUE (user_id),
    CONSTRAINT chk_merchants_business_type
        CHECK (business_type IN ('INDIVIDUAL', 'SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'COMPANY')),
    CONSTRAINT chk_merchants_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

CREATE INDEX idx_merchants_status ON identity_merchant.merchants (status, submitted_at);

-- A courier is identified by their user ID. Operational courier data (zones,
-- availability, assignments) belongs to delivery-operations-service.
CREATE TABLE identity_merchant.couriers (
    user_id                     UUID PRIMARY KEY REFERENCES identity_merchant.users (user_id),
    national_id_number          VARCHAR(20) NOT NULL,
    vehicle_type                VARCHAR(20) NOT NULL,
    driving_licence_number      VARCHAR(20),
    vehicle_registration_number VARCHAR(20),
    status                      VARCHAR(20) NOT NULL,
    last_decision_status        VARCHAR(20),
    last_decision_reason        VARCHAR(500),
    last_decided_at             TIMESTAMPTZ,
    last_decided_by             UUID,
    submitted_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_couriers_vehicle_type
        CHECK (vehicle_type IN ('BICYCLE', 'MOTORCYCLE', 'THREE_WHEELER', 'CAR', 'VAN', 'TRUCK')),
    CONSTRAINT chk_couriers_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT chk_couriers_vehicle_documents CHECK (
        vehicle_type = 'BICYCLE'
        OR (driving_licence_number IS NOT NULL AND vehicle_registration_number IS NOT NULL)
    )
);

CREATE INDEX idx_couriers_status ON identity_merchant.couriers (status, submitted_at);

-- Audit trail: every submission and every dispatcher decision, never updated or deleted.
CREATE TABLE identity_merchant.application_decisions (
    decision_id      UUID PRIMARY KEY,
    application_type VARCHAR(20)  NOT NULL,
    application_id   UUID         NOT NULL,
    action           VARCHAR(20)  NOT NULL,
    from_status      VARCHAR(20),
    to_status        VARCHAR(20)  NOT NULL,
    reason           VARCHAR(500),
    note             VARCHAR(500),
    actor_id         UUID         NOT NULL,
    decided_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_application_decisions_type CHECK (application_type IN ('MERCHANT', 'COURIER')),
    CONSTRAINT chk_application_decisions_action
        CHECK (action IN ('SUBMIT', 'RESUBMIT', 'APPROVE', 'REJECT', 'SUSPEND', 'REACTIVATE'))
);

CREATE INDEX idx_application_decisions_application
    ON identity_merchant.application_decisions (application_type, application_id, decided_at);
