# Delivery Operations Service

Spring Boot service for SwiftDrop delivery operations. It covers two areas in one application:

- **Warehouse operations** (Member 5): warehouses, parcel arrivals, sorting and transfers. Code is in `com.swiftdrop.delivery.{api,domain,repository,service}`.
- **Courier delivery** (Member 6): courier categories, courier profiles, assignments, status updates, delivery attempts, proof of delivery and delivery issues. Code is in `com.swiftdrop.delivery.courier.*`, so the two areas do not share folders.

## Technology

- Java 21 and Spring Boot 4.1
- Spring MVC, Validation, and Data JPA
- PostgreSQL with Flyway migrations
- Spring Security as an OAuth2 resource server (tokens from identity-merchant-service)
- RabbitMQ, with a transactional outbox for published events
- Actuator health checks

## Development

Create a root `.env` from `.env.example`, set `DELIVERY_DB_PASSWORD` and `RABBITMQ_PASSWORD`, and start the dependencies from the repository root:

```bash
docker compose up -d postgres rabbitmq
```

Run and test the application:

```bash
./mvnw spring-boot:run
./mvnw test
```

Environment variables:

| Name | Default |
| --- | --- |
| `SERVER_PORT` | `8083` |
| `DELIVERY_DB_URL` | `jdbc:postgresql://localhost:5432/swiftdrop` |
| `DELIVERY_DB_USER` | `delivery_user` |
| `DELIVERY_DB_PASSWORD` | Required; loaded from the root `.env` for local runs |
| `IDENTITY_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json`; where access-token keys are fetched |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5672` |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `swiftdrop`, empty; the password is required in practice |
| `DELIVERY_PARCEL_BASE_URL` | `http://localhost:8082`; Parcel & Pricing, used to look up parcels by tracking number |
| `DELIVERY_PARCEL_STUB_ENABLED` | `false`; when `true`, every tracking number is accepted and given a derived parcel id (for local work without Parcel & Pricing) |
| `DELIVERY_OUTBOX_ENABLED` | `true`; set `false` to stop publishing events |

## API groups

Warehouse operations (dispatchers only):

- `/api/v1/warehouses` — create, list, view, update, and change warehouse status
- `/api/v1/warehouse-parcels/arrivals` — record parcel arrivals
- `/api/v1/sorting-records` — record parcel sorting decisions
- `/api/v1/transfers` — create, dispatch, receive, and list parcel transfers
- `/api/v1/warehouses/{warehouseId}/dashboard` — warehouse operational summary

Recording an arrival and creating a transfer look the parcel up in Parcel & Pricing with the
dispatcher's bearer token. The returned Parcel & Pricing UUID is stored on both the warehouse
parcel and transfer; if Parcel & Pricing is unavailable, these endpoints return `503`.

Courier delivery:

- `/api/v1/courier-categories` — vehicle categories such as motorcycle, three-wheeler and van
- `/api/v1/courier-profiles` — set up approved couriers for delivery, and control their availability. This is deliberately not `/api/v1/couriers`, which belongs to identity-merchant-service
- `/api/v1/assignments` — assign a parcel to a courier, move it along, record a failed attempt or proof of delivery
- `/api/v1/delivery-issues` — couriers report problems, dispatchers review them

The shared OpenAPI contract is at `../../contracts/openapi/delivery-operations-service.yaml`; it lists the roles allowed on each operation.

## Access rules

Roles come from the `roles` claim of the access token. Role rules are in `config/SecurityConfig.java`; ownership rules (a courier sees and changes only their own assignments and issues) are in `courier/security/CourierAccess.java`.

| Who | Can do |
| --- | --- |
| Dispatcher | Everything in warehouse operations; manage categories and courier profiles; assign parcels; review issues; read all assignments, attempts and proofs |
| Courier | Read categories; look up their own profile; read, move along, and finish their own assignments; report issues |

The service never reads Identity's tables. A courier profile only stores the identity user id (the token `sub`).

## Assignment flow and events

```
ASSIGNED -> PICKED_UP -> IN_TRANSIT (optional) -> OUT_FOR_DELIVERY -> DELIVERED
                                                                  \-> DELIVERY_FAILED
```

- The status endpoint accepts only the steps up to `OUT_FOR_DELIVERY`. `DELIVERED` happens when proof of delivery is recorded, and `DELIVERY_FAILED` when a failed attempt is recorded, so those details are never skipped.
- A failed assignment is finished. To try again, a dispatcher creates a new assignment for the same parcel.
- Assigning a parcel looks it up in Parcel & Pricing (`GET /api/v1/dispatch/parcels/track/{ref}`, dispatcher only) with the dispatcher's own token, so the parcel must exist. The Parcel & Pricing id, customer, merchant and, for merchant Cash on Delivery orders, the amount due and pricing snapshot are stored with the assignment.
- The pickup and delivery contact (name, phone, address) is copied onto the assignment when it is created, so the assigned courier can find and call both ends. The API returns it only while the assignment is open and only to that courier or a dispatcher; it is dropped once the parcel is delivered or failed, and it is never put in an event.
- On a Cash on Delivery parcel the courier must enter the cash collected when recording proof of delivery; on any other parcel an amount is refused.

Cancelled and too-heavy parcels: assigning looks the parcel up with its status and weight. A cancelled parcel is refused (422, code `PARCEL_CANCELLED`), and so is a parcel heavier than the courier category's `maxWeightKg` (422, code `PARCEL_TOO_HEAVY`); a category with no limit, or a parcel with no known weight, is not checked. If Parcel & Pricing cancels a booking after it was assigned, `parcel.cancelled` (queue `delivery-operations-service.parcel-cancelled`, bound to `swiftdrop.parcel-pricing`) ends any open assignment as `CANCELLED`. That is repeat-safe, writes an audit row by `SYSTEM`, and publishes nothing, because Parcel & Pricing already announced the cancellation. An unreadable message is retried three times and then dead-lettered.

Proof of delivery can carry an uploaded file. `POST /api/v1/assignments/{id}/proof` accepts plain JSON, or `multipart/form-data` with a `proof` part (the same JSON) and an optional `file` part. The file must be a real JPEG, PNG or PDF of at most 5 MB. The type is read from the file's own bytes, not its name or header, and a refused file (400 `FILE_EMPTY`, 413 `FILE_TOO_LARGE`, 415 `UNSUPPORTED_FILE_TYPE`) saves nothing, so the parcel is not marked delivered. The bytes are stored in `DELIVERY_PROOF_STORAGE_DIR` (a Docker volume, `delivery_proofs`, in compose) under a random id that the database keeps as `file_key`; that reference is never returned, and no name or path the courier supplies is ever used on disk. Only the courier who owns the assignment and dispatchers can read it, at `GET /api/v1/assignments/{id}/proof/file`; it is not part of any event and public tracking never sees it. The old free-text `mediaUrl` still exists but nothing sends it any more.

Every status change is also written to `assignment_status_history` in the same transaction, with the signed-in user's identity id (the token's `sub`) and role. These come from the verified token, never from the request body (`assignedBy` is only a display name). Rows are only ever added. Dispatchers read it at `GET /api/v1/assignments/{id}/history`; couriers cannot.

Each step writes an event to the `outbox_events` table in the same transaction as the change. A scheduled publisher sends them to the topic exchange `swiftdrop.delivery-operations` and marks a row published only after the broker confirms it (at least once; consumers skip repeated `eventId`s). Routing keys: `delivery.assigned`, `delivery.picked-up`, `delivery.in-transit`, `delivery.out-for-delivery`, `delivery.delivered`, `delivery.failed`. The payloads are described in `../../contracts/events/delivery-operations/delivery-status-events.schema.json`, with examples in `../../contracts/examples/delivery-operations/`. Courier notes are never published; a failed delivery carries only a customer-safe `failureCode`.

For customer emails and merchant webhooks, each step except the assignment also writes `parcel.status-changed` to the exchange `swiftdrop.delivery`, which notification-webhook-service already listens on. Its contract is `../../contracts/events/parcel/parcel-status-changed.schema.json`, which has its own status names, so this service translates and nothing in that contract is renamed:

| Assignment status | `status` sent |
| --- | --- |
| `ASSIGNED` | not sent (that contract has no such status) |
| `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED` | the same name |
| `DELIVERY_FAILED` | `FAILED`, with a customer-safe `failureReason` |

`customerUserId` and `merchantId` come from Parcel & Pricing when the parcel is assigned and are left out when the booking has no registered customer or merchant.

When a Cash on Delivery parcel is delivered, `delivery.cod-collected` is also written to `swiftdrop.delivery-operations`, following `../../contracts/events/delivery-operations/cod-collected.schema.json` (payment-settlement-service consumes it). It carries only the contract fields: booking id, tracking number, pricing snapshot, merchant, courier, the proof id as `deliveryAttemptId`, expected and collected amounts, currency and time. It is written in the same transaction as the delivery, so cash is never reported for an undelivered parcel. Customer-booked COD parcels have no merchant amount and publish nothing. An example is in `../../contracts/examples/delivery-operations/parcel.status-changed.json`.

## Warehouse tracking events

Warehouse tracking changes use the same transactional outbox and the
`swiftdrop.delivery-operations` exchange. An arrival and a received transfer write
`parcel.received-at-warehouse` with `deliveryStatus: AT_WAREHOUSE`; dispatching a transfer writes
`parcel.transferred` with `deliveryStatus: IN_TRANSIT`. Sorting, creating a transfer and cancelling
a transfer do not publish an event.

Payloads follow
`../../contracts/events/delivery-operations/warehouse-parcel-events.schema.json`. They contain only
the Parcel & Pricing parcel UUID, tracking number, public status text and warehouse/transfer IDs.
The public location is a warehouse name; addresses, contact details, internal notes and
`receivedBy` are never published.

## Not done yet

- Migrations: `V4__cash_on_delivery.sql` adds the merchant and cash columns. `V5__assignment_status_history.sql` adds the audit trail, `V6__cancelled_assignments.sql` lets a cancelled assignment free its parcel, and `V7__warehouse_parcel_ids.sql` stores Parcel & Pricing UUIDs for warehouse tracking events. `V8__proof_of_delivery_file.sql` adds the proof file columns; the next migration is `V9`.
- If the collected amount differs from the amount due, the event still carries both; Payment & Settlement decides what to do.
