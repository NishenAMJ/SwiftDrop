-- Flyway Migration V6: Customer Profiles with persistent Avatar Image Storage
-- Schema: parcel_pricing

CREATE TABLE IF NOT EXISTS parcel_pricing.customer_profiles (
    customer_id UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(25),
    street_address VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    avatar_base64 TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_profiles_email ON parcel_pricing.customer_profiles(email);

