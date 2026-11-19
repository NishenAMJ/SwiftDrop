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
| `TRACKING_SSE_TIMEOUT` | `30m` | Maximum lifetime of one SSE connection before reconnect |
| `TRACKING_SSE_HEARTBEAT_INTERVAL` | `25s` | Interval between SSE heartbeat events |

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

The stream uses Spring MVC `SseEmitter`. It sends `connected`, an immediate
`tracking-snapshot`, committed `tracking-update` events, and periodic `heartbeat` events. A
reconnecting frontend receives a fresh snapshot and should compare each event's `updatedAt`
value before replacing newer state. Emitters are removed on completion, timeout, error or a
failed send. Configure the defaults with `TRACKING_SSE_TIMEOUT` and
`TRACKING_SSE_HEARTBEAT_INTERVAL`.

## Tracking APIs

Anonymous callers can use:

- `GET /api/v1/public/tracking/{trackingNumber}`
- `GET /api/v1/public/tracking/{trackingNumber}/history`

Authenticated callers can use:

- `GET /api/v1/tracking/{trackingNumber}`
- `GET /api/v1/tracking/{trackingNumber}/stream`

All responses are limited to the tracking number, public delivery state and description, safe general location labels and timestamps. Unknown tracking numbers return a generic `404` Problem Details response. See `contracts/openapi/tracking-reporting-service.yaml` for the complete contract.

## Reports

The following endpoints require a JWT with the `DISPATCHER` role:

- `GET /api/v1/reports/summary`
- `GET /api/v1/reports/status-breakdown`
- `GET /api/v1/reports/deliveries/daily`
- `GET /api/v1/reports/deliveries/monthly`

Each endpoint accepts optional inclusive `startDate` and `endDate` query parameters in ISO
`YYYY-MM-DD` format. Calculations use UTC. The exact cohort, success-rate and delivery-count
definitions are documented in `docs/requirements/tracking-report-calculations.md`.

## Security

Only the two documented `GET /api/v1/public/tracking/...` lookup endpoints and the Actuator
health endpoint allow anonymous access. Internal tracking and SSE endpoints require a valid JWT.
Report endpoints additionally require the `DISPATCHER` role from the token's `roles` claim. All
other routes are authenticated by default. JWT signatures are verified with the Identity service
JWKS endpoint configured by `IDENTITY_JWKS_URI`; issuer and audience validation use the values in
`application.yaml`.

## Current status

The service has tracking tables, event consumers, idempotent projection updates, privacy-safe
tracking APIs, authenticated live SSE delivery and dispatcher-only operational reports.
