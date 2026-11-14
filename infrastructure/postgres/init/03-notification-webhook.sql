\getenv communication_password COMMUNICATION_DB_PASSWORD

CREATE ROLE communication_user
    LOGIN PASSWORD :'communication_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE swiftdrop
    TO communication_user;

CREATE SCHEMA notification_webhook
    AUTHORIZATION communication_user;

REVOKE ALL ON SCHEMA notification_webhook FROM PUBLIC;

ALTER ROLE communication_user
    IN DATABASE swiftdrop
    SET search_path TO notification_webhook;
