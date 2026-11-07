\getenv payment_password PAYMENT_DB_PASSWORD

CREATE ROLE payment_settlement_user
    LOGIN PASSWORD :'payment_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE swiftdrop
    TO payment_settlement_user;

CREATE SCHEMA payment_settlement
    AUTHORIZATION payment_settlement_user;

REVOKE ALL ON SCHEMA payment_settlement FROM PUBLIC;

ALTER ROLE payment_settlement_user
    IN DATABASE swiftdrop
    SET search_path TO payment_settlement;
