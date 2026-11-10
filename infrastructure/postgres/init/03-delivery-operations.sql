\getenv delivery_password DELIVERY_DB_PASSWORD

CREATE ROLE delivery_user
    LOGIN PASSWORD :'delivery_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE swiftdrop
    TO delivery_user;

CREATE SCHEMA delivery_operations
    AUTHORIZATION delivery_user;

REVOKE ALL ON SCHEMA delivery_operations FROM PUBLIC;

ALTER ROLE delivery_user
    IN DATABASE swiftdrop
    SET search_path TO delivery_operations;
