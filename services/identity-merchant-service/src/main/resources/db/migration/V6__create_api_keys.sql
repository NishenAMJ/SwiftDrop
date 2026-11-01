-- Merchant API keys. The full key is shown to the merchant once; only its SHA-256
-- hash is stored. The prefix is kept so merchants can recognise their keys.
CREATE TABLE identity_merchant.api_keys (
    api_key_id   UUID PRIMARY KEY,
    merchant_id  UUID        NOT NULL REFERENCES identity_merchant.merchants (merchant_id),
    name         VARCHAR(60) NOT NULL,
    prefix       VARCHAR(20) NOT NULL,
    key_hash     VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_keys_merchant_id ON identity_merchant.api_keys (merchant_id);
