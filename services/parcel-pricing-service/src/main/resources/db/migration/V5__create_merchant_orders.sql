-- Merchant delivery orders.
CREATE SEQUENCE merchant_order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE merchant_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL,
    merchant_id BIGINT NOT NULL,
    merchant_reference VARCHAR(100),
    sender_name VARCHAR(100) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    sender_email VARCHAR(254),
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    receiver_email VARCHAR(254),
    pickup_address_line1 VARCHAR(150) NOT NULL,
    pickup_address_line2 VARCHAR(150),
    pickup_city VARCHAR(100) NOT NULL,
    pickup_district VARCHAR(100),
    pickup_postal_code VARCHAR(10),
    delivery_address_line1 VARCHAR(150) NOT NULL,
    delivery_address_line2 VARCHAR(150),
    delivery_city VARCHAR(100) NOT NULL,
    delivery_district VARCHAR(100),
    delivery_postal_code VARCHAR(10),
    parcel_description VARCHAR(500) NOT NULL,
    weight_kg NUMERIC(7, 2) NOT NULL,
    delivery_type VARCHAR(10) NOT NULL,
    urgent_note VARCHAR(255),
    payment_method VARCHAR(10) NOT NULL,
    cod_amount NUMERIC(12, 2),
    delivery_fee NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_merchant_orders_order_number UNIQUE (order_number),
    CONSTRAINT uq_merchant_orders_merchant_reference UNIQUE (merchant_id, merchant_reference),
    CONSTRAINT ck_merchant_orders_weight CHECK (weight_kg > 0),
    CONSTRAINT ck_merchant_orders_fee CHECK (delivery_fee >= 0),
    CONSTRAINT ck_merchant_orders_delivery_type CHECK (delivery_type IN ('NORMAL', 'URGENT')),
    CONSTRAINT ck_merchant_orders_payment_method CHECK (payment_method IN ('ONLINE', 'COD')),
    CONSTRAINT ck_merchant_orders_cod CHECK (
        (payment_method = 'COD' AND cod_amount IS NOT NULL AND cod_amount > 0)
        OR (payment_method = 'ONLINE' AND cod_amount IS NULL)
    ),
    CONSTRAINT ck_merchant_orders_status CHECK (
        status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED')
    ),
    CONSTRAINT ck_merchant_orders_source CHECK (source IN ('DASHBOARD', 'EXTERNAL_API'))
);

CREATE INDEX idx_merchant_orders_merchant_created
    ON merchant_orders (merchant_id, created_at DESC);
