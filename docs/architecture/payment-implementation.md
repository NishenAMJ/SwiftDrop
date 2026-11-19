# Member 8: payment implementation decisions

## Implemented contract and decisions

The OpenAPI contract in `contracts/openapi/payment-settlement-service.yaml` describes
the implemented API. Controllers and service-level authorization now enforce its
core behavior. Integration conventions still require team review.

- PayHere is the selected provider. The official documentation review and proposed
  pricing lookup are recorded in [payment-integrations.md](payment-integrations.md).
  Checkout returns an action URL, POST method and form fields for browser submission.
- Identity is owned by another member. Validate its JWT issuer, audience, signature,
  expiry and role claims using the shared authentication agreement.
- For merchant API bookings, customers may initiate payment for their own bookings
  and merchants for their own merchant's bookings. Dispatchers may initiate either
  booking channel; direct website bookings require dispatcher initiation.
- Customers and merchants read their own records; dispatchers can read all records.
- Only DISPATCHER issues full or partial delivery-fee refunds. Require an explicit
  amount and reason. Payment and refund are separate records: one payment can have
  several refund attempts. Pending refunds reserve funds; only confirmed refunds
  increase `refundedAmount`. A timeout is not proof of failure.
- Keep the existing packages and Member 9 ownership boundaries. Add future Flyway
  migrations rather than rewriting V1. V2 and Java implement PARTIALLY_REFUNDED and
  CHARGEBACKED, ownership, refund reservations and outbox persistence.

## Integration status

Parcel & Pricing is one service (`parcel-pricing-service`, port 8082), currently
unavailable. A client and local-only fixture implement the proposed payment-context
shape, awaiting agreement with the owner. It provides booking reference, channel, customer and
merchant ownership, payable state, authoritative amount/currency and pricing
snapshot identifier. Do not trust client-provided ownership or amounts. Use a
development/test stub for this agreement; production must require the real service.
The proposed endpoint, service authentication and snapshot validity are documented
in the integration draft; they are not existing capabilities or team-approved APIs.

Use RabbitMQ and a transactional outbox. Save each payment state change and its
event in one database transaction. A worker publishes persistent messages with
publisher confirms and retries; mark an outbox record delivered only after successful
routing and broker confirmation. A crash can cause redelivery, so reuse eventId and
require consumers to deduplicate. The implementation follows the shared envelope;
[event schemas and examples](../../contracts/events/payment-settlement-service/README.md)
document swiftdrop.payment and its four routing keys.

Persist provider attempts before external operations. Recover pending/uncertain
operations using provider-supported status checks and verified callbacks. Never
blindly repeat a potentially successful charge or refund. Exact reconciliation
capabilities and limitations are documented in the integration notes. Real-provider
sandbox validation remains outstanding.

## Returned parcels and merchant fines

Delivery Operations collects receiver and courier return confirmations. An eligible
merchant fine requires both reports to agree on a merchant-responsible reason.
Courier-caused damage incurs no merchant fine; unclear or conflicting responsibility
remains unresolved without an automatic fine. No admin approval is required for
eligible fines. Parcel & Pricing calculates the fine, Member 9 records the ledger
charge once, and Notification informs the merchant. These are integration boundaries,
not features to implement inside Member 8's online-payment module. A ledger charge
does not authorize an automatic PayHere debit.

## Learning sequence

1. Review API inputs, permissions and refund responses.
2. Finalize the pricing contract and verify PayHere checkout/refund capabilities.
3. Add persistence migrations and entities, explaining payment versus attempts/refunds.
4. Add JWT authorization alongside the first protected endpoints.
5. Implement pricing lookup and payment checkout with idempotency.
6. Implement verified callbacks, outbox delivery and recovery.
7. Implement authorized history and full/partial refunds.
8. Complete RabbitMQ/Compose configuration and end-to-end sandbox checks.

For each step, explain the change and its purpose, implement a small coherent piece,
run relevant checks, and review the result before starting the next piece.

## Verification and next work

The user reported 22 tests passing, none skipped, on 2026-11-11 at 16:07:52
Asia/Colombo. This covers the current automated suite, including PostgreSQL and
RabbitMQ publishing recovery; it does not verify real identity/pricing services,
PayHere sandbox behavior or broker-restart recovery. See the
[service README](../../services/payment-settlement-service/README.md) for commands,
configuration, code-flow explanations and operational limits. Next steps are Docker
startup checks and team/provider integration, not additional contract implementation.
