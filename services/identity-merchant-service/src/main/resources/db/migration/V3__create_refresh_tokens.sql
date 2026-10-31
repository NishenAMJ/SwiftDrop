-- Opaque refresh tokens. Only the SHA-256 hash is stored, never the token itself.
-- Every rotation creates a new row in the same family; reusing an old token
-- revokes the whole family (see RefreshTokenService).
CREATE TABLE identity_merchant.refresh_tokens (
    refresh_token_id UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES identity_merchant.users (user_id) ON DELETE CASCADE,
    family_id        UUID        NOT NULL,
    token_hash       VARCHAR(64) NOT NULL,
    auth_method      VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by      UUID,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_auth_method CHECK (auth_method IN ('password', 'google'))
);

CREATE INDEX idx_refresh_tokens_user_id ON identity_merchant.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON identity_merchant.refresh_tokens (family_id);
