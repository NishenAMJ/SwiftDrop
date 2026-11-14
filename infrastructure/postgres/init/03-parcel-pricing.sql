\getenv parcel_password PARCEL_DB_PASSWORD

CREATE ROLE parcel_pricing_user
    LOGIN PASSWORD :'parcel_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE swiftdrop
    TO parcel_pricing_user;

CREATE SCHEMA parcel_pricing
    AUTHORIZATION parcel_pricing_user;

REVOKE ALL ON SCHEMA parcel_pricing FROM PUBLIC;

ALTER ROLE parcel_pricing_user
    IN DATABASE swiftdrop
    SET search_path TO parcel_pricing;
