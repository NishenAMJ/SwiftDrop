# notification-webhook-service

Owner: Member 10. Port **8086**. Package `com.swiftdrop.communication`.

Listens to SwiftDrop events on RabbitMQ and:

- sends **email** (and a **mock SMS**, logged with a masked number) to customers and merchants;
- delivers **signed webhooks** to merchants' servers when their parcels change status, retrying
  with increasing delays when the merchant's server fails;
- offers a REST API for merchants to manage their webhooks and see the delivery log.

Design notes, diagrams and patterns: [`docs/architecture/notification-webhook.md`](../../docs/architecture/notification-webhook.md).
Test report and coverage: [`docs/testing/notification-webhook-service.md`](../../docs/testing/notification-webhook-service.md).
API contract: [`contracts/openapi/notification-webhook-service.yaml`](../../contracts/openapi/notification-webhook-service.yaml).

## Events consumed

| Queue (owned by this service) | Exchange | Routing keys | What happens |
|---|---|---|---|
| `notification-webhook-service.identity-events` | `swiftdrop.identity` | `user.*`, `merchant.*` | Keep a copy of contact details; welcome email; merchant approved / rejected / suspended email; pause or resume a suspended merchant's webhooks |
| `notification-webhook-service.parcel-notifications` | `swiftdrop.parcel`, `swiftdrop.delivery` | `parcel.status-changed` | Email (all statuses) and mock SMS (out for delivery, delivered, failed) to the customer |
| `notification-webhook-service.parcel-webhooks` | `swiftdrop.parcel`, `swiftdrop.delivery` | `parcel.status-changed` | Queue a signed webhook delivery for each active subscription of the parcel's merchant |
| `notification-webhook-service.dead-letter` | (default exchange) | | Messages that still fail after 3 tries, or cannot be read |

Every event is handled **once**: its `eventId` is stored per consumer in `processed_events`.

## REST API

All endpoints need a JWT from identity-merchant-service. Merchants only ever see their own
data: another merchant's ID answers **404**.

| Method and path | Role | Purpose |
|---|---|---|
| `POST /api/v1/webhooks/subscriptions` | MERCHANT | Register a URL; the response shows the signing **secret once** |
| `GET /api/v1/webhooks/subscriptions` | MERCHANT | List my subscriptions (paged) |
| `GET /api/v1/webhooks/subscriptions/{id}` | MERCHANT | One subscription |
| `PATCH /api/v1/webhooks/subscriptions/{id}` | MERCHANT | Change URL, event types, or pause/resume |
| `DELETE /api/v1/webhooks/subscriptions/{id}` | MERCHANT | Delete |
| `POST /api/v1/webhooks/subscriptions/{id}/rotate-secret` | MERCHANT | New secret (shown once) |
| `POST /api/v1/webhooks/subscriptions/{id}/test` | MERCHANT | Send a test webhook |
| `GET /api/v1/webhooks/subscriptions/{id}/deliveries` | MERCHANT | Delivery log |
| `GET /api/v1/webhooks/deliveries/{id}` | MERCHANT | One delivery, with its payload |
| `POST /api/v1/webhooks/deliveries/{id}/retry` | MERCHANT | Retry a FAILED delivery now |
| `GET /api/v1/webhooks/deliveries?status=FAILED` | DISPATCHER | All failed deliveries (monitoring) |
| `GET /actuator/health` | public | Health check |

A Postman collection with the full merchant flow is in [`postman/`](postman/).

### Webhook request sent to merchants

```http
POST <merchant URL>
Content-Type: application/json
X-SwiftDrop-Event: parcel.status-changed
X-SwiftDrop-Delivery: <delivery id>
X-SwiftDrop-Timestamp: <unix seconds>
X-SwiftDrop-Signature: t=<timestamp>,v1=<hex HMAC-SHA256(secret, timestamp + "." + body)>
```

Any 2xx answer is success. Otherwise the delivery is retried after 10 s, 30 s, 1 min, 2 min and
5 min (6 attempts in total), then marked FAILED. The body never contains customer IDs or personal
data.

## Configuration

Set in the root `.env` (local) or by `compose.yaml` (Docker). Real secrets have no default.

| Variable | Purpose | Default |
|---|---|---|
| `COMMUNICATION_DB_PASSWORD` | Password of `communication_user` (**required**) | none |
| `COMMUNICATION_DB_URL` / `COMMUNICATION_DB_USER` | Database connection | `jdbc:postgresql://localhost:5432/swiftdrop` / `communication_user` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | Broker | `localhost` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Broker login | `guest` / `guest` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server (Mailpit locally) | `localhost` / `1025` |
| `NOTIFICATION_FROM_EMAIL` | Sender address | `noreply@swiftdrop.dev` |
| `TRACKING_BASE_URL` | Tracking link in emails | `http://localhost:4200/track` |
| `IDENTITY_JWKS_URI` | Public keys to check JWTs | `http://localhost:8081/.well-known/jwks.json` |
| `WEBHOOK_ENCRYPTION_KEY` | Base64 32-byte key that encrypts webhook secrets (**required**) | none: startup fails with a clear message |
| `WEBHOOK_ALLOW_HTTP` | Allow `http://` webhook URLs | `true` (set `false` in production) |
| `WEBHOOK_ALLOW_PRIVATE_ADDRESSES` | Allow localhost/private IPs (SSRF guard) | `true` (set `false` in production) |
| `WEBHOOK_POLL_INTERVAL` | How often due deliveries are sent | `5s` |
| `WEBHOOK_RETRY_DELAYS` | Retry schedule | `10s,30s,1m,2m,5m` |
| `NOTIFICATION_PROFILES` | Docker only: `dev` enables `/dev/webhook-receiver` | `default` |

Generate a key in PowerShell:

```powershell
$b=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b);[Convert]::ToBase64String($b)
```

## Run

**Locally** (from the repo root, then the service folder):

```powershell
docker compose up -d postgres rabbitmq mailpit
cd services\notification-webhook-service
.\mvnw.cmd spring-boot:run                                   # normal
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"  # with the demo webhook receiver
```

**In Docker** (from the repo root):

```powershell
docker compose up -d --build notification-webhook-service
docker compose logs -f notification-webhook-service
```

Check: http://localhost:8086/actuator/health returns `{"status":"UP"}`.

| Tool | URL |
|---|---|
| Mailpit (sent emails) | http://localhost:8025 |
| RabbitMQ UI (login from `.env`) | http://localhost:15672 |

## Test

Docker Desktop must be running (tests start their own PostgreSQL and RabbitMQ).

```powershell
.\mvnw.cmd verify
start target\site\jacoco\index.html
```

71 tests, 83.7% line coverage. See the test report for details.

## Quick demo

1. Start the service (Docker or local) plus identity-merchant-service.
2. RabbitMQ UI → Exchanges → `swiftdrop.identity` → Publish `user.registered`
   (example: `src/test/resources/events/user.registered.json`, new `eventId`). A welcome email
   appears in Mailpit.
3. Exchange `swiftdrop.delivery` → routing key `parcel.status-changed` →
   `src/test/resources/events/parcel.out-for-delivery.json`. An "Out for Delivery" email appears,
   and the log shows the masked mock SMS.
4. Postman collection: register and approve a merchant, create a subscription pointing to
   `http://localhost:8086/dev/webhook-receiver` (dev profile), publish a parcel event with that
   `merchantId`, and watch the delivery succeed in `/deliveries`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `password authentication failed for user "communication_user"` | Database password differs from `.env`: `docker compose exec postgres psql -U postgres -d swiftdrop -c "ALTER ROLE communication_user PASSWORD '<.env value>';"` |
| `WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key` | Add the key to `.env` (command above) |
| Emails don't arrive when running **locally** | `.env` must not set `MAIL_HOST=mailpit` (that name only works inside Docker); remove it so `localhost` is used |
| JWTs rejected when running **locally** | Same for `IDENTITY_JWKS_URI`: don't set a Docker host name in `.env` |
| An event was ignored | Its `eventId` was already processed. Publish again with a new `eventId` |
| A message disappeared | Look in queue `notification-webhook-service.dead-letter` |
