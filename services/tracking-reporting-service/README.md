# Tracking and Reporting Service

The Tracking and Reporting service maintains parcel tracking projections, delivery history and operational reports for SwiftDrop. It runs on port **8084** and uses the Java package `com.swiftdrop.tracking`.

## Requirements

- Java 21
- PostgreSQL 17
- RabbitMQ
- Access to the Identity service JWKS endpoint for JWT validation

The service uses the `swiftdrop` PostgreSQL database with its own `tracking_reporting` schema and restricted `tracking_user` account. Set `TRACKING_DB_PASSWORD` in the repository root `.env`; never commit that file.

Local defaults connect to PostgreSQL and RabbitMQ on `localhost`. The service validates JWT access tokens with the Identity service JWKS endpoint at `http://localhost:8081/.well-known/jwks.json`.

| Variable | Default | Purpose |
|---|---|---|
| `TRACKING_DB_PASSWORD` | none | Required password for `tracking_user` |
| `TRACKING_DB_URL` | `jdbc:postgresql://localhost:5432/swiftdrop` | PostgreSQL connection URL |
| `TRACKING_DB_USER` | `tracking_user` | Restricted database user |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USER` | `guest` | RabbitMQ username for local development |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password for local development |
| `IDENTITY_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | Identity service public signing keys |

## Build

From this directory:

```bash
./mvnw -DskipTests compile
```

Confirm that `./mvnw --version` reports Java 21 before building.

## Run

After PostgreSQL, RabbitMQ and the Identity service are available:

```bash
./mvnw spring-boot:run
```

The application will be available at `http://localhost:8084`.

## Event consumption

The service owns two durable RabbitMQ queues:

- `tracking-reporting-service.parcel-pricing-events` for `parcel.*` events from `swiftdrop.parcel-pricing`
- `tracking-reporting-service.delivery-events` for `parcel.*` and `delivery.*` events from `swiftdrop.delivery-operations`

Invalid messages and messages that still fail after three attempts are rejected to queue-specific dead-letter queues. Event IDs are claimed transactionally in PostgreSQL, so duplicate deliveries do not create duplicate history entries.

Committed state changes are sent to SSE subscribers at:

```text
GET /api/v1/tracking/{trackingNumber}/stream
```

## Current status

The service has its tracking tables, event consumers, idempotent projection updates and live SSE delivery. Tracking query APIs, report APIs and final endpoint authorization are implemented in later phases.
