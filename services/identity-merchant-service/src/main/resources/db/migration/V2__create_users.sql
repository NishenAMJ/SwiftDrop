CREATE TABLE identity_merchant.users (
    user_id              UUID PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    password_hash        VARCHAR(100),
    full_name            VARCHAR(100) NOT NULL,
    phone_number         VARCHAR(16),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    google_subject       VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_google_subject UNIQUE (google_subject),
    CONSTRAINT chk_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    -- Every account can log in somehow: a password, Google, or both.
    CONSTRAINT chk_users_login_method CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL)
);

-- Roles that are active right now. Approving a merchant or courier adds a row;
-- suspending removes it. Access tokens carry exactly these roles.
CREATE TABLE identity_merchant.user_roles (
    user_id    UUID        NOT NULL REFERENCES identity_merchant.users (user_id) ON DELETE CASCADE,
    role_id    SMALLINT    NOT NULL REFERENCES identity_merchant.roles (role_id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON identity_merchant.user_roles (role_id);
