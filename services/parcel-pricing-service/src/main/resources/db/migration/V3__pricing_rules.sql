-- Configurable pricing rules.
CREATE TABLE IF NOT EXISTS parcel_pricing.pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    base_fee NUMERIC(10, 2) NOT NULL DEFAULT 300.00,
    rate_per_kg NUMERIC(10, 2) NOT NULL DEFAULT 50.00,
    default_zone_charge NUMERIC(10, 2) NOT NULL DEFAULT 150.00,
    urgent_surcharge NUMERIC(10, 2) NOT NULL DEFAULT 200.00,
    urgent_surcharge_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    min_weight_kg NUMERIC(6, 2) NOT NULL DEFAULT 0.01,
    max_weight_kg NUMERIC(6, 2) DEFAULT 50.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pricing_rules_code ON parcel_pricing.pricing_rules(rule_code);
CREATE INDEX IF NOT EXISTS idx_pricing_rules_active ON parcel_pricing.pricing_rules(active);
CREATE INDEX IF NOT EXISTS idx_pricing_rules_is_default ON parcel_pricing.pricing_rules(is_default);

INSERT INTO parcel_pricing.pricing_rules (
    rule_name, rule_code, description, base_fee, rate_per_kg,
    default_zone_charge, urgent_surcharge, urgent_surcharge_enabled,
    min_weight_kg, max_weight_kg, active, is_default
) VALUES (
    'Standard Domestic Pricing',
    'STANDARD_DEFAULT',
    'Standard SwiftDrop delivery pricing: Base fee LKR 300, Rate per kg LKR 50, Zone charge LKR 150',
    300.00, 50.00, 150.00, 200.00, FALSE, 0.01, 50.00, TRUE, TRUE
) ON CONFLICT (rule_code) DO NOTHING;
