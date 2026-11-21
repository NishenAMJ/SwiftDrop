# Payment & Settlement service — Members 8 and 9

Java 21 / Spring Boot 4.1.2, port **8085**. Member 8 owns online payments through
PayHere. Member 9 owns COD collection, courier cash reconciliation, the merchant
ledger and merchant settlements.

## Current state and references

- [OpenAPI](../../contracts/openapi/payment-settlement-service.yaml)
- [Payment events](../../contracts/events/payment-settlement-service/README.md)
- [Pricing integration proposal and provider notes](../../docs/architecture/payment-integrations.md)
- [Authentication agreement](../../docs/architecture/authentication.md)
- [Member 9 COD and ledger contract proposal](../../docs/architecture/member9-cod-ledger-contract.md)

On 2026-11-15, before Member 9's implementation, `mvn -B clean test` against the
isolated payment test database and RabbitMQ reported **32 tests, 0 failures, 0 errors,
0 skipped**. The combined suite now defines 53 tests and must be rerun with Java 21
and isolated infrastructure. Historical results do not verify the new V5 migration,
COD workflows, live PayHere, actual identity/pricing integration, gateway routing or
Docker-image end-to-end readiness.

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
development database to fix a missing schema. Flyway applies V1 through V5; Hibernate validates.

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
| PAYMENT_COD_CONSUMER_ENABLED | false; enable only after COD producer and pricing verification contracts are approved |
| PAYMENT_COD_VERIFICATION_MODE | pending; fail-closed placeholder that prevents enabling the consumer until an approved verifier is implemented |

JWT issuer is `swiftdrop-identity`, audience `swiftdrop-api`. Role checks use `roles`,
ownership uses `sub` and `merchant_id`. No locally fabricated JWT bypass exists.
HTTP connect/request timeouts are currently hardcoded to 2/5 seconds.

## PayHere sandbox refunds

The Refund API uses the App ID and App Secret from a PayHere API Key, not the
Merchant ID/Secret used to sign checkout. PayHere's current Refund API guide requires
the API Key permission **Automated Charging API**. For sandbox domain-resolution
problems, PayHere advises setting that API Key's allowed domain to `*`. This API Key
setting is separate from the checkout Domain/App entry used for the Merchant Secret.
See the [PayHere Refund API guide](https://support.payhere.lk/api-%26-mobile-sdk/refund-api).

For a payment refund, the service sends `payment_id`, `description`, and `amount` for
partial refunds, with a Bearer token obtained from the API Key credentials. If a
refund call is rejected, first verify the sandbox base URL, that the API Key has the
required permission, and that its allowed domain is valid for sandbox. OAuth success
and payment retrieval success do not prove that the Refund API is authorized. Do not
retry a request whose outcome is uncertain; keep it pending and use provider evidence
and the dispatcher recovery endpoints. The sandbox call still needs to be verified
with a real test payment before treating refunds as integration-tested.

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
   block another payment even with a new key. A failed claim can be released only by a
   dispatcher after a verified terminal failure callback, empty PayHere retrieval and
   provider evidence; the prior payment remains in history.
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
| POST /{id}/refunds/{refundId}/retry | Dispatcher only; uncertain refund, explicit confirmation and PayHere preflight required |
| POST /{id}/refunds/{refundId}/resolve | Dispatcher only; resolve an uncertain submitted refund with provider evidence |
| POST /{id}/booking-claim/release | Dispatcher only; release a terminally failed payment's booking claim with provider evidence |
| GET /{id}/recovery-audit | Dispatcher only; read recovery actions and cited evidence |
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

## Member 9 COD and settlement flow

1. Delivery Operations publishes `delivery.cod-collected` with a stable eventId and
   the approved COD data contract.
2. `CodCollectionService` validates the event and an approved `CodAmountVerifier`
   validates its pricing snapshot. Collection and positive `COD_COLLECTION` ledger
   entry are stored in one transaction; duplicate eventIds have no additional effect.
3. The courier submits their own unassigned collections in a cash handover.
4. A dispatcher records received cash. Exact reconciliation confirms the handover and
   makes its COD ledger entries settlement-eligible. Short/excess handovers are
   flagged with an investigation note and remain ineligible.
5. A dispatcher calculates a settlement from eligible, unsettled signed ledger entries,
   approves the frozen result, then separately records an externally confirmed payout.
6. Recording payment posts one negative `SETTLEMENT` ledger entry. The service never
   claims to initiate or complete the external bank transfer.

The ledger sign is from the merchant-payable perspective: COD is positive; delivery
charges and paid settlements are negative; audited adjustments may use either sign.
Posted financial fields and deletion are protected by a PostgreSQL trigger. Corrections
must be new `ADJUSTMENT` entries.

| Method / path | Access |
|---|---|
| POST `/api/v1/cod-handovers` | Courier; own collections only |
| GET `/api/v1/cod-handovers`, GET `/api/v1/cod-handovers/{id}` | Owning courier or dispatcher |
| POST `/api/v1/cod-handovers/{id}/confirm` | Dispatcher only |
| GET `/api/v1/merchant-ledger/entries` | JWT merchant or dispatcher-selected merchant |
| POST `/api/v1/merchant-ledger/adjustments` | Dispatcher only |
| POST `/api/v1/merchant-settlements/calculate` | Dispatcher only |
| GET `/api/v1/merchant-settlements/{id}` | Owning merchant or dispatcher |
| POST `/api/v1/merchant-settlements/{id}/approve` | Dispatcher only |
| POST `/api/v1/merchant-settlements/{id}/record-payment` | Dispatcher only; external evidence required |

All Member 9 financial POST routes require `Idempotency-Key`. See the OpenAPI document
for bodies, status values and error responses.

### COD RabbitMQ boundary

- Topic exchange: `swiftdrop.delivery-operations`
- Routing key: `delivery.cod-collected`
- Consumer queue: `payment-settlement-service.cod`
- Dead-letter exchange/routing key: `swiftdrop.payment.dlx` / `cod.failed`
- Dead-letter queue: `payment-settlement-service.cod.dlq`

Manual acknowledgements occur only after the transactional service returns (and its
database transaction has committed). Malformed, invalid and conflicting messages are
dead-lettered. Transient failures are requeued. `PAYMENT_COD_CONSUMER_ENABLED` remains
false, and pending verification mode fails startup if someone enables it. Implement
and approve service-to-service pricing verification before enabling this consumer.

### Member 9 persistence

Flyway `V5__cod_reconciliation_and_merchant_ledger.sql` creates `cod_collections`,
`cash_handovers`, `cash_handover_items`, `cash_reconciliations`,
`merchant_ledger_entries`, `merchant_settlements` and `merchant_settlement_entries`.
It follows V4 from Member 8; do not create another V5 or edit an already-applied V5.

## Recovery limits

Uncertain refunds keep their reservation and are not blindly submitted again.
Successful Refund API responses with a refund reference confirm the operation; this
does not assert recipient-bank settlement. Lost partial-refund responses may need
operational investigation: there is no per-attempt provider lookup. Dispatchers can
record a manual outcome only after reviewing PayHere dashboard/support evidence; the
decision, evidence reference and reason are stored in `payment_recovery_audit` in the
same transaction as the status and reservation update. The endpoint does not contact
PayHere and must never be used as a substitute for provider evidence. A failed
pre-refund retrieval can also leave a claimed attempt pending.
Recovery can confirm an outstanding refund covering the remaining balance when the
provider reports the matching payment REFUNDED. Multiple provider matches are not
automatically selected. Never edit reservations directly in the database or resend
money blindly; use the dispatcher recovery API with verifiable evidence.

A booking claim can be released only for a payment marked FAILED by a verified
PayHere terminal failure callback (-1 or -2), with no payment reference and no result
from a fresh retrieval. An empty retrieval alone is not proof: dispatchers must cite
dashboard/support evidence and confirm the provider's terminal failure. The claim is
soft-released rather than deleted, and the action is audited. Investigate any later
conflicting provider evidence; never release a claim for an uncertain or pending payment.

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

The gateway owner should forward `/api/v1/payments/**`, `/api/v1/cod-handovers/**`,
`/api/v1/merchant-ledger/**` and `/api/v1/merchant-settlements/**` unchanged to port
8085 and preserve Authorization and Idempotency-Key. Permit only the POST provider
callback without a customer JWT. Preserve form encoding; do not automatically retry
financial POST requests. Keep payment and settlement logic in this service. No gateway
configuration has been installed by this module.

## Tests with isolated infrastructure

Never point PAYMENT_TEST_DB_URL at a development/shared database: integration tests
TRUNCATE all payment, COD, ledger and settlement tables. From the service directory, start these containers only if
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

The suite defines 53 tests, including 20 PostgreSQL-backed integration tests. These
counts describe the source, not a successful run. Without the database variable, the
20 integration tests skip; without
`PAYMENT_TEST_RABBIT=true`, the broker recovery test skips. Provider/pricing
dependencies are mocked in the database tests; no live money is moved. A retry
warning is expected in the broker test because its first publication deliberately
fails.

```powershell
# Only the RabbitMQ recovery test:
mvn test '-Dtest=PaymentIntegrationTest#eventSurvivesPublishFailureThenReachesDurableQueue'
# Member 9 unit/security/database tests (requires PAYMENT_TEST_DB_URL for the integration class):
mvn test '-Dtest=CodCollectionServiceTest,CashHandoverServiceTest,MerchantLedgerServiceTest,MerchantSettlementServiceTest,Member9SecurityTest,Member9IntegrationTest'
# After testing (only these disposable containers):
Remove-Item Env:PAYMENT_TEST_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:PAYMENT_TEST_RABBIT -ErrorAction SilentlyContinue
docker stop swiftdrop-payment-check-rabbit swiftdrop-payment-check-db
```

Their --rm flag removes them on stop. RabbitMQ test UI: http://localhost:15685,
paymenttest / payment-test-only. The test consumes its message, so the queue can be
empty afterward. For sandbox acceptance still test real checkout, cancellation,
callbacks, repeated partial refunds and ambiguous outcomes with PayHere credentials.
