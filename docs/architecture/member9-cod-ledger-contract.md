# Member 9: COD, reconciliation and merchant-ledger contract

## Status and ownership

This is a **proposal for team review**, not evidence that upstream services already
publish the required data. Member 9 owns the Payment & Settlement records and APIs.
Parcel & Pricing owns the authoritative booking amount and pricing snapshot. Delivery
Operations owns courier assignment, delivery attempts and proof that cash was
collected. Payment & Settlement must not query either service's database.

Reviewers required before integration: Parcel/Pricing owners (Members 3 and 4),
Delivery Operations owner (Member 6), and Payment co-owner (Member 8).

## COD collection boundary

Delivery Operations publishes `delivery.cod-collected` to the durable topic exchange
`swiftdrop.delivery-operations` with the same routing key after an authorized courier
records a completed COD collection. The version 1 payload is defined in
`contracts/events/delivery-operations/cod-collected.schema.json`.

- The envelope `eventId` is the stable idempotency key and AMQP `message_id`.
- `bookingId`, `merchantId`, amount, currency and `pricingSnapshotId` originate from
  the immutable Parcel & Pricing payment context. Delivery carries copies so the
  event is self-describing; Payment validates them against Parcel & Pricing before
  posting financial records.
- `courierId` and `deliveryAttemptId` originate from Delivery Operations.
- `collectedAmount` and `collectedAt` are the courier-recorded facts confirmed by the
  Delivery Operations workflow.
- `paymentMethod` is always `COD`. A generic delivered event is not proof of cash
  collection and must not create a collection or ledger entry.
- The proposed Payment-owned durable queue is `payment-settlement-service.cod`, bound
  only to `delivery.cod-collected`. Acknowledge after the database transaction commits.
  Malformed messages are rejected without requeue and routed to the team's agreed DLQ;
  transient upstream or database failures are retried with bounded redelivery.

The producer and consumer owners must confirm the exchange, queue, DLQ and pricing
lookup behavior before enabling the consumer outside isolated tests.

## Handover and reconciliation workflow

1. An authenticated `COURIER` submits a handover containing only that courier's
   unassigned COD collections and a submitted amount. `Idempotency-Key` protects the
   command from retries.
2. A handover is immutable once submitted. Collections cannot appear in two active
   handovers.
3. A `DISPATCHER` records the received amount and optional investigation note.
4. Reconciliation compares the sum of included `collectedAmount` values with the
   received amount. The result is `EXACT`, `SHORT` or `EXCESS`.
5. Exact handovers become `CONFIRMED`. Short or excess handovers become `FLAGGED` and
   remain visible for investigation. No shortage is automatically assigned to a
   courier or merchant.
6. The service stores the confirming dispatcher and confirmation time. Posted facts
   are corrected through explicit audited actions, never silent edits or deletes.

Couriers can submit and read only their own handovers. Dispatchers can list, inspect
and confirm all handovers. Merchants cannot access courier cash-handover records.

## Ledger convention

Ledger amounts are signed from the merchant-payable perspective:

| Entry type | Sign | Meaning |
|---|---:|---|
| `COD_COLLECTION` | positive | SwiftDrop owes the collected merchandise value to the merchant |
| `DELIVERY_CHARGE` | negative | Charge retained by SwiftDrop |
| `ADJUSTMENT` | positive or negative | Audited correction with a reason and source reference |
| `SETTLEMENT` | negative | Approved merchant payable has been discharged |

Each entry has an immutable amount, currency, type, source reference and idempotency
key. A posted entry is never updated or deleted to fix a financial discrepancy. A new
`ADJUSTMENT` entry references the original source and records the reason. Ledger
queries are restricted to the authenticated merchant's `merchant_id`; dispatchers
may query another merchant explicitly.

## Settlement rules

- Only COD collections in a confirmed exact handover, or a separately reviewed and
  resolved reconciliation, are eligible for settlement.
- Settlement calculation selects eligible, unsettled ledger entries through a UTC
  cutoff and freezes the selected entry IDs and total.
- The payable total is the sum of signed entries and must be positive to create a
  settlement.
- Calculation (`CALCULATED`), approval (`APPROVED`) and externally confirmed payment
  (`PAID`) are distinct states. This service does not move money by itself.
- Marking a settlement `PAID` requires an external payment reference and paid time.
  The resulting negative `SETTLEMENT` ledger entry is posted exactly once.
- Currency mixing is forbidden. Version 1 is expected to use LKR, but storage and
  contracts retain an explicit three-letter currency.

## API proposal

The OpenAPI contract defines:

- courier submission/list/detail and dispatcher confirmation under
  `/api/v1/cod-handovers`;
- merchant/dispatcher ledger reads and dispatcher adjustments under
  `/api/v1/merchant-ledger`;
- settlement calculation, detail, approval and external-payment recording under
  `/api/v1/merchant-settlements`.

All financial commands require an `Idempotency-Key`. Error responses use the existing
RFC 9457-style `Problem` shape. These contracts must be reviewed before frontend or
other services depend on them.
