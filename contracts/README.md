# SwiftDrop contracts

This folder holds the agreements between services: the REST APIs they expose and the events they publish. A contract is reviewed and merged **before** the code that implements it, so dependent teams can build against it in parallel.

> **Status: proposal.** These conventions were drafted alongside the Identity & Merchant contract. They apply to every service once the team approves the pull request.

## Layout

```
contracts/
├── README.md                     # this file: shared conventions
├── openapi/
│   └── <service>.yaml            # one OpenAPI 3.1 file per service
├── events/
│   ├── envelope.schema.json      # shared envelope every event uses
│   └── <service>/                # JSON Schemas for that service's events
└── examples/
    └── <service>/                # sample event payloads, valid against the schemas
```

## REST conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1/<resource>`, plural nouns: `/api/v1/merchants/{merchantId}` |
| Actions | State changes that need their own permission or a reason are `POST` sub-resources: `POST /merchants/{id}/approve`. Use `PATCH` only for plain field edits. |
| JSON | `camelCase` property names. Omit absent optional fields instead of sending `null`. |
| IDs | UUIDs (string, `format: uuid`). Never expose database sequence numbers. |
| Time | ISO-8601 in UTC with `Z`: `2026-11-01T09:30:00Z`. |
| Enums | `UPPER_SNAKE_CASE` strings. |
| Errors | RFC 9457 Problem Details (`application/problem+json`), plus a machine-readable `code` and, for validation, an `errors` list. See below. |
| Paging | Query `?page=0&size=20&sort=createdAt,desc` (`page` starts at 0, max `size` 100). Response: `{ "content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }`. |
| Auth | `Authorization: Bearer <JWT>` on every endpoint unless the contract marks it public (`security: []`). See [docs/architecture/authentication.md](../docs/architecture/authentication.md). |
| Personal data | Responses return only what the caller is allowed to see. Public endpoints never return addresses, phone numbers or emails. |

### Error format

```json
{
  "type": "https://swiftdrop.dev/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/auth/register",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
}
```

Spring Boot supports this format through `ProblemDetail`. Never put stack traces, SQL or internal class names in `detail`.

Common codes that all services share: `VALIDATION_FAILED` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409), `RATE_LIMITED` (429, with a `Retry-After` header in seconds), `INTERNAL_ERROR` (500). Services add their own specific codes in their contract.

## Event conventions

| Topic | Rule |
|---|---|
| Broker | RabbitMQ. Each service publishes to its own **topic exchange**, named `swiftdrop.<domain>` (durable). |
| Routing key | The event type: `<entity>.<past-tense-verb>`, for example `merchant.approved`. |
| Envelope | Every message body follows [events/envelope.schema.json](events/envelope.schema.json): `eventId`, `eventType`, `version`, `occurredAt`, `producer`, `data`. |
| Message properties | `content_type=application/json`, `message_id=<eventId>`, `delivery_mode=2` (persistent). |
| Queues | Owned by the **consumer**, named `<consumer-service>.<purpose>`, for example `notification-webhook-service.identity-events`, bound with routing-key patterns such as `merchant.*`. |
| Delivery | At least once. Consumers must be idempotent: store processed `eventId`s and skip repeats. |
| Data | Only what consumers need. IDs, not database rows. No passwords, tokens or API keys, ever. |

## Changing a contract

- **Additive changes** (new optional field, new endpoint, new event type) are fine within the same version. Consumers must ignore fields they don't know.
- **Breaking changes** (removing or renaming a field, changing a type, making an optional field required) need a new REST version (`/api/v2/...`) or a new event `version`, and the old one keeps working until all consumers have moved.
- Every change goes through a pull request that the owners of the affected consumers review.
