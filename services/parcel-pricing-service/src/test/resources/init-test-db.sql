-- Test database only. In Docker the schema and its user come from
-- infrastructure/postgres/init/03-parcel-pricing.sql; Flyway is configured not to create schemas.
CREATE SCHEMA IF NOT EXISTS parcel_pricing;
