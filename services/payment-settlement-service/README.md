# Payment & Settlement service — Member 8

Java 21 / Spring Boot 4.1.2, port **8085**. Online payments use PayHere; COD, ledger,
cash reconciliation and merchant settlements remain Member 9 responsibilities.

## Current state and references

- [OpenAPI](../../contracts/openapi/payment-settlement-service.yaml)
- [Payment events](../../contracts/events/payment-settlement-service/README.md)
- [Pricing integration proposal and provider notes](../../docs/architecture/payment-integrations.md)
- [Authentication agreement](../../docs/architecture/authentication.md)

On 2026-11-11 the user's full `mvn clean test` run reported **22 tests, 0 failures,
0 errors, 0 skipped**, including PostgreSQL and RabbitMQ. This does not establish
live PayHere, actual identity/pricing integration, gateway routing or Docker-image
end-to-end readiness. The pricing endpoint is still a proposal.

## Run locally (PowerShell)

Start Docker Desktop. From the repository root, create `.env` only if it is absent:

```powershell
cd D:\Projects\EAD\SwiftDrop
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Edit `.env`: set POSTGRES_ADMIN_PASSWORD, IDENTITY_DB_PASSWORD, PAYMENT_DB_PASSWORD,
RABBITMQ_USER and RABBITMQ_PASSWORD. Existing Docker volumes retain their original
credentials; editing `.env` does not rotate database or broker passwords.
Use unquoted KEY=value entries; do not commit `.env`.

```powershell
docker compose up -d postgres rabbitmq
docker compose ps
cd services\payment-settlement-service
mvn spring-boot:run
```

Run Maven from the service folder, which contains `pom.xml`. From the root instead,
use `mvn -f services/payment-settlement-service/pom.xml spring-boot:run`.
The service imports the root `.env` when launched from either directory. Environment
variables override file values. The PostgreSQL initialization scripts create the
service role/schema only on an empty database volume; existing installations must
already have `payment_settlement_user` and `payment_settlement`. Do not erase a
development database to fix a missing schema. Flyway applies V1/V2; Hibernate validates.

In another terminal:

```powershell
Invoke-RestMethod http://localhost:8085/actuator/health
Invoke-RestMethod http://localhost:8085/actuator/health/readiness
```

Readiness includes the database and RabbitMQ, not pricing/PayHere/JWKS availability.
Health routes are public; payment routes need an identity-issued JWT. Other Actuator
routes are denied. Authentication is not implemented by this service.

## Configuration

| Variable | Local default / purpose |
|---|---|
| PAYMENT_DB_URL / PAYMENT_DB_USER | jdbc:postgresql://localhost:5432/swiftdrop / payment_settlement_user |
| PAYMENT_DB_PASSWORD | Required database secret |
| RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USER | localhost / 5672 / swiftdrop |
| RABBITMQ_PASSWORD | Required broker secret |
| IDENTITY_JWKS_URI | http://localhost:8081/.well-known/jwks.json |
| PAYMENT_PRICING_BASE_URL | http://localhost:8082 |
| PAYMENT_PRICING_STUB_ENABLED | false; requires local profile if enabled |
| PAYMENT_PRICING_STUB_RESOURCE | classpath:local/payment-context.json; local JVM override supported |
| PAYMENT_PAYHERE_BASE_URL | https://sandbox.payhere.lk |
| PAYMENT_PAYHERE_MERCHANT_ID / PAYMENT_PAYHERE_MERCHANT_SECRET | Checkout/signature credentials |
| PAYMENT_PAYHERE_APP_ID / PAYMENT_PAYHERE_APP_SECRET | Retrieval/refund OAuth credentials |
| PAYMENT_PAYHERE_NOTIFY_URL | Public callback URL ending /api/v1/payments/callbacks/payhere |
| PAYMENT_PAYHERE_RETURN_URL / PAYMENT_PAYHERE_CANCEL_URL | localhost:4200/payments/return and /payments/cancel (frontend routes must be implemented) |
| PAYMENT_WORKERS_ENABLED | true; disables outbox and recovery workers when false |

JWT issuer is `swiftdrop-identity`, audience `swiftdrop-api`. Role checks use `roles`,
ownership uses `sub` and `merchant_id`. No locally fabricated JWT bypass exists.
HTTP connect/request timeouts are currently hardcoded to 2/5 seconds.

For the pricing fixture, before launching Maven:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:PAYMENT_PRICING_STUB_ENABLED = 'true'
```

The fixture booking is `SWD-LOCAL-000001`. Its customer/merchant UUIDs must match your
real test token, or use a dispatcher token. Edit an external synthetic fixture and
set PAYMENT_PRICING_STUB_RESOURCE=file:/D:/path/payment-context.json if needed.
The local fixture does not replace JWT verification or PayHere credentials. It is
rejected without the local profile and when prod/production is active.

## API flow and learning guide

1. Controller validates the request; Actor checks permissions/ownership.
2. PricingClient obtains trusted price, ownership, channel and payer details.
3. PaymentService stores PENDING, the snapshot and idempotency key. Per-booking claims
   block another payment even with a new key. There is no failed-payment reset API yet.
4. PayHereProvider returns a POST action and signed form fields. The browser submits
   them to PayHere; backend creation alone does not charge a card.
5. Verified callbacks update payment state and create an outbox event atomically.
6. OutboxPublisher delivers it to RabbitMQ; PaymentRecovery checks uncertain payments.
7. Dispatcher refund requests reserve funds, verify the provider payment reference,
   and submit the refund. Each successful partial refund increments refundedAmount.

| Method / path under /api/v1/payments | Access |
|---|---|
| POST / | Merchant API: owning customer/merchant or dispatcher; direct website: dispatcher |
| GET /{id}, GET /history | Owner or dispatcher; history filtered before pagination |
| GET /{id}/checkout | Owner or dispatcher; pending payments only |
| POST /{id}/refund | Dispatcher only; amount and reason required |
| GET /{id}/refunds/{refundId} | Owner or dispatcher |
| POST /callbacks/payhere | Public to JWT auth; verified provider form required |

Create body: `{"bookingId":"SWD-LOCAL-000001"}`. Refund body:
`{"amount":250.00,"reason":"Delivery fee adjustment"}`. Both POST commands require
Idempotency-Key (16-128 characters: letters, digits, dot, underscore, colon, hyphen).
Use a fresh key for a new command and the same key when retrying that command.
Creation/replays return 201; non-pending replays omit checkout. Refund/replays return
202 with their current PENDING/SUCCESS/FAILED status. Inspect the body, not just HTTP.
GET checkout allows a customer to complete a dispatcher-created payment; frontend
checkout screens and the notification/handoff to the customer are still separate work.

## Recovery limits

Uncertain refunds keep their reservation and are not blindly submitted again.
Successful Refund API responses with a refund reference confirm the operation; this
does not assert recipient-bank settlement. Lost partial-refund responses may need
operational investigation: no per-attempt reconciliation or manual resolution API
exists. A failed pre-refund retrieval can also leave a claimed attempt pending.
Recovery can confirm an outstanding refund covering the remaining balance when the
provider reports the matching payment REFUNDED. Multiple provider matches are not
automatically selected. Do not manually clear reservations or resend money blindly.

Recovery processes at most one eligible payment per pass (default ten seconds),
deferring its next lookup five minutes; it also submits previously unclaimed refunds.
These conservative workers and their throughput need review for production scale.

## Docker and gateway coordination

From the root, after configuration:

```powershell
docker compose up -d --build payment-settlement-service
docker compose logs --tail 100 payment-settlement-service
```

Compose uses `postgres`/`rabbitmq` service DNS. Identity and pricing default to
`host.docker.internal:8081` and `:8082` because they are not included in this Compose
file. Override their URLs if the integration owner puts them on a shared network.
The payment image uses Dockerfile.payment. These commands are startup instructions,
not evidence that the current image has completed end-to-end testing.

The gateway owner should forward `/api/v1/payments` and `/api/v1/payments/**` unchanged
to port 8085 and preserve Authorization and Idempotency-Key. Permit only the POST
provider callback without a customer JWT. Preserve form encoding; do not retry
payment/refund POST requests automatically. Keep payment logic in this service.
No gateway configuration has been installed by this module.

## Tests with isolated infrastructure

Never point PAYMENT_TEST_DB_URL at a development/shared database: integration tests
TRUNCATE payment tables. From the service directory, start these containers only if
they do not already exist (`docker ps -a`); reuse healthy existing test containers.

```powershell
docker run -d --rm --name swiftdrop-payment-check-db -e POSTGRES_PASSWORD=payment-test-only -e POSTGRES_DB=payment_test -p 127.0.0.1:55485:5432 postgres:17
docker run -d --rm --name swiftdrop-payment-check-rabbit -e RABBITMQ_DEFAULT_USER=paymenttest -e RABBITMQ_DEFAULT_PASS=payment-test-only -p 127.0.0.1:56785:5672 -p 127.0.0.1:15685:15672 rabbitmq:4-management
docker exec swiftdrop-payment-check-db pg_isready -U postgres -d payment_test
docker exec swiftdrop-payment-check-rabbit rabbitmq-diagnostics -q check_running
docker exec swiftdrop-payment-check-rabbit rabbitmq-diagnostics -q check_port_connectivity
```

Wait for readiness checks to succeed. In the same terminal:

```powershell
$env:PAYMENT_TEST_DB_URL = 'jdbc:postgresql://localhost:55485/payment_test'
$env:PAYMENT_TEST_RABBIT = 'true'
mvn clean test
```

The current suite has 22 tests. Without the database variable, integration tests skip;
without PAYMENT_TEST_RABBIT=true, the broker test skips. Provider/pricing dependencies
are mocked in the database tests; no live money is moved. A retry warning is expected
in the broker test because its first publication deliberately fails.

```powershell
# Only the RabbitMQ recovery test:
mvn test '-Dtest=PaymentIntegrationTest#eventSurvivesPublishFailureThenReachesDurableQueue'
# After testing (only these disposable containers):
Remove-Item Env:PAYMENT_TEST_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:PAYMENT_TEST_RABBIT -ErrorAction SilentlyContinue
docker stop swiftdrop-payment-check-rabbit swiftdrop-payment-check-db
```

Their --rm flag removes them on stop. RabbitMQ test UI: http://localhost:15685,
paymenttest / payment-test-only. The test consumes its message, so the queue can be
empty afterward. For sandbox acceptance still test real checkout, cancellation,
callbacks, repeated partial refunds and ambiguous outcomes with PayHere credentials.
