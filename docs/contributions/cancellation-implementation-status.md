# Cancellation implementation status and continuation

Status: **incomplete; cancellation is intentionally unavailable**. No customer is charged or refunded by the new cancellation endpoints.

## Existing implementation audit

- V8 and Parcel already contained six nullable cancellation columns. Their names and types match; V8 was preserved without modification. Existing records are retained. Database execution and Flyway upgrade were not tested against PostgreSQL.
- CancellationEligibilityDto existed but no cancellation service/controller used it. It now includes booking details and nullable financial/schedule data. Null means unknown, not a zero-value refund.
- CancelBookingComponent posted to the nonexistent `/api/bookings/cancel` endpoint and treated any response as completed cancellation. This behavior was removed.
- Booking history contained literal patch `+` characters and an extra closing brace, preventing compilation. These were repaired; cancellation details are accessible from history, and cancelled bookings have no active cancellation action.
- PaymentSettlementClient had two no-op methods which silently assumed success. Both now throw explicitly.
- PayHere is the actual payment provider. PaymentService supports checkout, verified callbacks, refund transaction reservations, idempotency keys, uncertain refund recovery, and admin-only partial refunds. Cancellation does not bypass that authorization or reuse a customer's token as an administrator.
- The repository has courier account applications and tracking event projections, but no authoritative pickup schedule, journey-start/reservation API, or pickup execution service. A projection cannot safely prove that a courier has not started travelling.
- Existing JWT authentication uses UUID subjects. Cancellation endpoints require authentication and enforce customer ownership before returning details. Existing unrelated booking authentication behavior was preserved.
- Existing gateway routing already forwards `/api/v1/bookings/**` to parcel-pricing-service.

## Implemented source changes

- BookingCancellationService checks authentication, ownership, booking stage and cancelled status. Without authoritative pickup/journey data it rejects eligibility and initiation. No false successful cancellation is returned.
- BookingCancellationController provides eligibility, initiation, status and receipt routes under `/api/v1/bookings/{id}`. Initiation and receipt return structured conflict errors when unavailable. Status never asserts a verified completion.
- Parcel rejects attempts to reactivate an already-cancelled entity.
- Customer cancellation details use the authenticated eligibility response, show actual supported parcel information and restriction reason, distinguish unknown financial data from zero, and support loading/error/retry/close behavior. This is an inline details panel, not yet a modal confirmation/payment workflow.
- Existing V8 migration, booking creation, original receipts and unrelated customer profile edits were preserved.

## Verification

- Backend compile and targeted tests: passed. 6 BookingCancellationServiceTest tests and 3 existing BookingServiceTest tests, zero failures.
- TypeScript application check: passed (`npm exec -- tsc --noEmit -p tsconfig.app.json`).
- Angular production build: passed. Initial sandbox attempt failed with esbuild EPERM; rerun with approved permissions succeeded.
- Angular cancellation UI tests: 2 passed, zero failures (backend eligibility loading/restriction and safe network errors).
- No live PostgreSQL migration, payment sandbox, webhook, courier race or end-to-end customer cancellation was verified.

## Exact continuation checklist

1. Supply or implement the authoritative pickup scheduling/journey service and its authenticated API. It must atomically reserve a booking against journey start, reassignment and schedule changes, and release/commit that reservation. Do not infer absence of travel from missing tracking events.
2. Set an approved positive cancellation fee and explicit fee-exceeds-paid policy. No arbitrary fee was chosen.
3. Add a new migration after V8 for cancellation operations, unique idempotency keys, state/version, booking reservation, original successful payment reference, fee/refund references and timestamps. Preserve existing financial transaction ownership in payment-settlement-service.
4. Add a service-authenticated cancellation payment contract to payment-settlement-service: authoritative amount/context, separate COD fee checkout purpose, original payment lookup, customer-authorized cancellation partial refund with booking reservation verification, operation lookup and authenticated results. Existing ordinary payment booking claims must not be reused in a way that charges the original amount again.
5. Implement durable pending/processing/failed/reconciliation/completed transitions. Reserve locally and remotely before money moves; perform network I/O outside database transactions; never blindly resubmit unknown outcomes. Reconcile verified success after crashes without charging again.
6. Implement strict server-time `pickup > now + 24h` tests with an injected Clock, and revalidate/reserve against courier races. Assigned alone is allowed only when the authoritative service confirms no journey start.
7. Finalize only from verified fee/refund results and a valid reservation. Add database protection against SQL reactivation and integrate cancelled/reserved checks into the actual courier/pickup/delivery writers once those exist.
8. Complete an accessible modal with explicit confirmation, stable retry idempotency, checkout handling, durable polling/resume and separate pending/failure states. Enable confirmation only after the backend supports safe eligibility.
9. Implement completed cancellation receipt DTO/UI with original details and financial references, print support and history access. Never use the ordinary booking receipt as cancellation evidence.
10. Update active counts/notifications and run the complete requested security, payment, migration, race, browser and external sandbox tests. Current tests verify safe rejection, not the successful end-to-end cancellation flow.

Do not describe the feature as complete until the above workflow is implemented and verified. No commits, pushes or merges were performed.

