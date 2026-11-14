# Tracking and Reporting Service

The Tracking and Reporting service maintains parcel tracking projections, delivery history and operational reports for SwiftDrop. It runs on port **8084** and uses the Java package `com.swiftdrop.tracking`.

## Requirements

- Java 21
- PostgreSQL 17
- RabbitMQ
- Access to the Identity service JWKS endpoint for JWT validation

The PostgreSQL, RabbitMQ and security settings are runtime configuration and will be added with the tracking implementation. The service will use the `tracking_reporting` schema, the `tracking_user` database account and the `TRACKING_DB_PASSWORD` environment variable.

## Build

From this directory:

```bash
./mvnw -DskipTests compile
```

Confirm that `./mvnw --version` reports Java 21 before building.

## Run

After the required database, messaging and JWT configuration is supplied:

```bash
./mvnw spring-boot:run
```

The application will be available at `http://localhost:8084`.

## Current status

This branch contains the initial Spring Boot scaffold. Tracking tables, Flyway migrations, RabbitMQ consumers, security rules, APIs, live updates and reports will be implemented after the scaffold is merged into `dev` and the shared API and event contracts are agreed.
