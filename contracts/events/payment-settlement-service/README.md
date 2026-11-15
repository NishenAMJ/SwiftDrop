# Payment events (version 1)

These contracts describe the current publisher; consumer-team agreement is still
required. Validate each event against [payment-event.schema.json](payment-event.schema.json),
which also references the [shared envelope](../envelope.schema.json).
Synthetic examples are in [contracts/examples/payment-settlement-service](../../examples/payment-settlement-service).

| Routing key / eventType | Trigger | data.status |
|---|---|---|
| payment.succeeded | Verified callback or retrieval establishes success | SUCCESS |
| payment.failed | Verified cancellation/failure callback while payment is pending | FAILED |
| payment.refunded | Provider-confirmed refund or supported full-refund reconciliation | PARTIALLY_REFUNDED, REFUNDED, or CHARGEBACKED if a chargeback raced with the refund |
| payment.chargebacked | Verified chargeback callback or retrieval | CHARGEBACKED |

All events include paymentId, bookingId, original amount, currency, payment status,
and cumulative refundedAmount. customerId and merchantId are included when known.
Refund events additionally include refundId and refundAmount (this individual refund).
The original amount is not reduced by refunds. Events contain no payer contact data,
JWTs, provider credentials or raw callback payloads. SUCCESS for a refund means the
provider API confirmed the operation, not that the recipient's bank settled it.

## Transport and consumption

- Durable topic exchange: `swiftdrop.payment`; routing key equals eventType.
- JSON body, `content_type=application/json`, `message_id=eventId`, persistent delivery.
- Payment's durable holding/audit queue: `payment-settlement-service.audit`, bound to
  `payment.*`. There is no consumer or retention policy for this queue yet; monitor
  its growth and agree retention before production. It is not a notification queue.
- Each downstream service must declare its own durable queue and bind it to this
  exchange. For example, a notification-owned queue may bind `payment.*`.
  Separate queues give each service a copy. Consumers sharing one queue compete.
- Bind queues before publishing: a queue added later does not automatically receive
  old events already sitting in the audit queue. Historical replay is not implemented.
- Acknowledge messages after persisting both the business effect and processed eventId
  atomically. Duplicate eventIds must have no additional effect. Consumer retries and
  dead-letter handling are the consumer's responsibility and are not implemented here.
- Do not assume strict ordering. A delayed failed event can arrive after a recovered
  success. Treat events as notifications and fetch authorized current state when a
  consumer needs it. No per-payment sequence number exists in version 1.

## Publisher reliability

The payment change and outbox insert share a database transaction. The worker claims
one event per pass (default every two seconds), uses a 30-second lease and waits up to
five seconds for broker confirmation. Nacks, returned messages and exceptions retain
the event for retry. The current exponential delay reaches a maximum of 256 seconds.
A crash after broker acceptance but before marking published can deliver the same
event again. Publisher confirmation means broker acceptance, not consumer completion.
The audit binding can satisfy routing even when no downstream consumer is configured.

The integration test simulates a publish failure, verifies the pending outbox row,
retries against real RabbitMQ, verifies publication, and reads the queued message.
It does not simulate a broker restart or verify notification-service consumption.
