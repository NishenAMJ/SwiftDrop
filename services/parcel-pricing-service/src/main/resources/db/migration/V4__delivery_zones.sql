-- Delivery zones used by the pricing module.
CREATE TABLE IF NOT EXISTS parcel_pricing.delivery_zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    description VARCHAR(500),
    zone_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    base_charge NUMERIC(10, 2) NOT NULL DEFAULT 150.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_zone_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_zone_type CHECK (zone_type IN ('GENERAL', 'DISTRICT', 'POSTAL_CODE'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_zones_code ON parcel_pricing.delivery_zones(code);
CREATE INDEX IF NOT EXISTS idx_delivery_zones_status ON parcel_pricing.delivery_zones(status);

INSERT INTO parcel_pricing.delivery_zones
    (name, code, description, status, base_charge, zone_type)
VALUES
    ('Colombo District', 'COLOMBO', 'Commercial hub and Western Province primary zone',
     'ACTIVE', 150.00, 'DISTRICT'),
    ('Gampaha District', 'GAMPAHA', 'Western Province suburban and industrial zone',
     'ACTIVE', 200.00, 'DISTRICT'),
    ('Kandy District', 'KANDY', 'Central Province main city and highland zone',
     'ACTIVE', 300.00, 'DISTRICT')
ON CONFLICT (code) DO NOTHING;
