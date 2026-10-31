\getenv identity_password IDENTITY_DB_PASSWORD

CREATE ROLE identity_merchant_user
    LOGIN PASSWORD :'identity_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE swiftdrop
    TO identity_merchant_user;

REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA identity_merchant
    AUTHORIZATION identity_merchant_user;

REVOKE ALL ON SCHEMA identity_merchant FROM PUBLIC;

ALTER ROLE identity_merchant_user
    IN DATABASE swiftdrop
    SET search_path TO identity_merchant;
