CREATE TABLE identity_merchant.roles (
    role_id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    CONSTRAINT chk_role_name CHECK (
        name IN ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER')
    )
);

INSERT INTO identity_merchant.roles (name)
VALUES
    ('CUSTOMER'),
    ('MERCHANT'),
    ('COURIER'),
    ('DISPATCHER');
