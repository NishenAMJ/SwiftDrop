# Payment integration draft

Status: payment-side client/adapter implemented; pricing API remains a proposal for
the upstream owner. Real-provider sandbox verification is outstanding.

## PayHere review

Reviewed on 2026-11-10 against official documentation:

- [Checkout API](https://support.payhere.lk/api-%26-mobile-sdk/checkout-api):
  browser form POST, backend-generated hash, form-encoded callbacks. Callback URL
  must be publicly reachable. Browser return/cancel redirects are not payment proof.
- [Refund API](https://support.payhere.lk/api-%26-mobile-sdk/refund-api):
  OAuth client credentials; refund using payment_id and description, with amount
  for a partial refund. Keep the returned refund reference. Never expose credentials.
- [Retrieval API](https://support.payhere.lk/api-%26-mobile-sdk/retrieval-api):
  search by order_id can return several payments because order IDs are not unique
  at PayHere. Match payment reference, currency and amount. No result does not
  prove failure. Refund-requested/processing states are not completed refunds.

SwiftDrop choices:

- Use the payment UUID as order_id and keep the PayHere payment_id separately.
- Return checkoutUrl, method=POST and a string-valued fields map. Supply the payer's
  name, email, phone and billing address from the trusted booking snapshot below.
  Never use the initiating dispatcher's personal details as the payer's details.
- Format provider monetary values with exactly two decimal places using BigDecimal.
- Build hashes server-side. Verify callbacks before applying state changes, including
  comparison against stored amount, currency, order ID and configured merchant ID.
- Configure return/cancel URLs server-side. GET /api/v1/payments/{paymentId}/checkout
  supplies authenticated owners with form data after dispatcher initiation. The
  customer-facing screen/handoff is not implemented; do not turn fields into a
  public URL or grant direct-booking initiation rights to customers.
- Require PAYMENT_PAYHERE_MERCHANT_ID, PAYMENT_PAYHERE_MERCHANT_SECRET,
  PAYMENT_PAYHERE_APP_ID and PAYMENT_PAYHERE_APP_SECRET through environment secrets.
  Keep sandbox/live base URLs and callback/return/cancel URLs separately configured.

### Refund recovery limitation

The documentation does not establish a provider idempotency key or a per-attempt
partial-refund lookup. Do not infer either. Serialize refund submissions per payment;
reserve funds before submission. On timeout, retain PENDING and the reservation.
Reconcile using verified provider evidence; if evidence cannot identify the exact
refund amount and attempt, retain it for operational investigation without resubmission.
The adapter currently maps Refund API status=1 with a numeric refund reference to
SUCCESS, confirming that provider operation, not bank settlement. Explicit status
0/-1 maps to FAILED; exceptions and ambiguous responses retain PENDING. Pre-refund
retrieval failures can also leave a claimed attempt pending. Retrieval-based
recovery only resolves a matching aggregate REFUNDED payment when one outstanding
refund covers the remaining balance. A dispatcher-only manual resolution endpoint
records the cited PayHere evidence and reason in the recovery audit before resolving
an uncertain attempt; it does not contact PayHere or independently prove the evidence.
Failed booking claims can be released only after a verified terminal failure callback,
an empty fresh retrieval, and dispatcher-cited provider evidence. The claim remains
as released history rather than being deleted.
Repeated partial refunds and ambiguous timeout recovery remain mandatory sandbox
acceptance checks; automated tests use a mocked provider for these workflows.

## Proposed Parcel & Pricing lookup

`GET /api/v1/bookings/{bookingId}/payment-context`

Forward the initiating caller's bearer JWT; Parcel & Pricing must validate it and
authorize the booking. This proposal avoids inventing a service credential flow.
Payment independently checks the returned ownership and booking channel.

Example 200 response (synthetic data):

```json
{
  "bookingId": "SWD-2026-000125",
  "channel": "MERCHANT_API",
  "customerId": "f2249d4c-a134-4bc0-b9ed-ffea97ad8791",
  "merchantId": "846c790e-497a-4340-8039-79e25c95f40e",
  "payable": true,
  "pricingSnapshotId": "d0dafc29-38c0-4e45-bebd-594bca0f620c",
  "amount": 1000.00,
  "currency": "LKR",
  "payer": {
    "firstName": "Test",
    "lastName": "Customer",
    "email": "payer@example.com",
    "phone": "+94770000000",
    "address": "Test address",
    "city": "Colombo",
    "country": "Sri Lanka"
  }
}
```

- channel is MERCHANT_API or DIRECT_WEBSITE. customerId identifies the authorized
  customer account, not an arbitrary recipient. merchantId is required for
  MERCHANT_API and omitted for DIRECT_WEBSITE. If merchant bookings have no linked
  customer account, omit customerId and allow only the owning merchant/dispatcher
  to initiate or read; never identify ownership using email alone.
- All other example fields are required. Version one accepts LKR with a positive
  amount and at most two decimal places. Payer contact fields are private.
- Pricing snapshot is immutable and must remain valid for its initiated payment;
  later pricing edits cannot alter an issued checkout. This reservation guarantee
  requires agreement with the pricing owner before real integration.
- A matching idempotent replay uses the saved snapshot rather than re-pricing.
- Upstream 401/403/404 remain corresponding safe client errors. Not payable or an
  invalidated snapshot produces 409. Timeout, malformed data or upstream outage
  produces 502; never substitute an invented price. Initial connect/read timeouts:
  2/5 seconds, currently hardcoded, with no automatic retries in the first client.
- Use this shape in test fixtures and an explicitly enabled local stub. Production
  must not enable the stub. Do not query Parcel & Pricing's database directly.

## Integration acceptance checks

- Pricing lookup: both booking channels, owning/non-owning callers, missing payer
  fields, invalid amount/currency, not payable, timeout and idempotent replay.
- Checkout: safe form fields, decimal formatting and no credentials in responses.
- Callback: authentic, tampered, duplicate, mismatched and out-of-order notifications.
- Refund: full, partial, repeated partial, over-refund, concurrent requests and timeout.
- Recovery: duplicate provider matches, unavailable retrieval and uncertain refunds
  must not create another charge or refund.

A local-only pricing fixture and HTTP client now exist. No upstream endpoint or
provider account was created, and no live-provider request has been verified. The
pricing owner must review this proposed agreement. See the service README for
fixture activation and test instructions.
