# Tracking Event Contract Review

> **Status: proposal for team review.** Owners: Parcel & Pricing, Delivery Operations, and Tracking & Reporting. Approve these decisions before implementing publishers or consumers.

## Purpose

Tracking & Reporting builds its current parcel state, customer-safe history and reports from events published by Parcel & Pricing and Delivery Operations. The JSON Schemas in `contracts/events/parcel-pricing/` and `contracts/events/delivery-operations/` define the proposed version 1 payloads.

## Proposed events

| Producer | Exchange | Event | Tracking status |
|---|---|---|---|
| Parcel & Pricing | `swiftdrop.parcel-pricing` | `parcel.booked` | `BOOKED` |
| Delivery Operations | `swiftdrop.delivery-operations` | `parcel.received-at-warehouse` | `AT_WAREHOUSE` |
| Delivery Operations | `swiftdrop.delivery-operations` | `parcel.transferred` | `IN_TRANSIT` |
| Delivery Operations | `swiftdrop.delivery-operations` | `delivery.assigned` | `ASSIGNED` |
| Delivery Operations | `swiftdrop.delivery-operations` | `delivery.picked-up` | `PICKED_UP` |
| Delivery Operations | `swiftdrop.delivery-operations` | `delivery.in-transit` | `IN_TRANSIT` |
| Delivery Operations | `swiftdrop.delivery-operations` | `delivery.out-for-delivery` | `OUT_FOR_DELIVERY` |
| Delivery Operations | `swiftdrop.delivery-operations` | `delivery.delivered` | `DELIVERED` |
| Delivery Operations | `swiftdrop.delivery-operations` | `delivery.failed` | `DELIVERY_FAILED` |

Hyphens are used inside event verbs because the shared envelope permits lowercase letters and hyphens, not underscores.

## Required envelope and tracking data

Every event uses the shared envelope and includes `eventId`, `eventType`, `version`, `occurredAt`, `producer` and `data`. Every `data` object includes:

- `parcelId`: the UUID owned by Parcel & Pricing
- `trackingNumber`: the public `SWD-...` reference
- `deliveryStatus`: the status enforced for that event type
- `publicDescription`: customer-safe timeline text
- `publicLocation` when a safe general location is available

`publicLocation` is limited to a city, zone, warehouse name or route label. Events for tracking must not contain full addresses, phone numbers, customer names, proof files, precise courier positions or internal issue notes.

## Event meaning decisions

- `parcel.booked` is published only after the booking transaction commits.
- `parcel.received-at-warehouse` is published for the first warehouse arrival and whenever a dispatched transfer is received at its destination.
- `parcel.transferred` means the transfer was dispatched and the parcel is moving between the named source and destination warehouses.
- `delivery.failed` carries a customer-safe `failureCode`; private operational notes remain inside Delivery Operations.
- `occurredAt` records when the business change happened in UTC. It is not the broker publish time.

## RabbitMQ bindings

Tracking & Reporting owns its queues:

- `tracking-reporting-service.parcel-pricing-events` binds `parcel.*` to `swiftdrop.parcel-pricing`.
- `tracking-reporting-service.delivery-events` binds `parcel.*` and `delivery.*` to `swiftdrop.delivery-operations`.

Messages use `eventId` as the AMQP `message_id`, persistent delivery and `application/json` content type, following the shared contract conventions.

## Idempotency requirement

RabbitMQ delivery is at least once. Tracking & Reporting must store each processed `eventId` under a unique database constraint. The processed-event insert, current projection update and history insert must commit in one database transaction. A repeated `eventId` is acknowledged without adding another history row or changing report totals.

## Required owner decision

Parcel & Pricing already owns both `parcelId` and `trackingNumber`. The current Delivery Operations warehouse model stores only the tracking reference as `parcelReference`. Before approval, the Delivery Operations owner must either:

1. carry and store `parcelId` with its parcel records and publish both identifiers, or
2. propose removing `parcelId` from Delivery Operations events and document how consumers correlate records safely.

The proposed schemas require option 1 because the team workload specifies `parcelId` on every event.

## Approval checklist

- [ ] Parcel & Pricing owner approves `parcel.booked` fields and publish timing.
- [ ] Delivery Operations owner approves warehouse event meanings and identifier propagation.
- [ ] Delivery Operations owner approves courier delivery event names, statuses and failure data.
- [ ] Tracking & Reporting owner confirms every payload is sufficient for public history and reports.
- [ ] All owners confirm that no private customer or operational data is published.
- [ ] Approved schemas are merged before publisher and consumer implementation begins.
