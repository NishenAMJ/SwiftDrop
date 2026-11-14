\getenv tracking_password TRACKING_DB_PASSWORD

CREATE ROLE tracking_user
    LOGIN PASSWORD :'tracking_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE swiftdrop
    TO tracking_user;

CREATE SCHEMA tracking_reporting
    AUTHORIZATION tracking_user;

REVOKE ALL ON SCHEMA tracking_reporting FROM PUBLIC;

ALTER ROLE tracking_user
    IN DATABASE swiftdrop
    SET search_path TO tracking_reporting;
