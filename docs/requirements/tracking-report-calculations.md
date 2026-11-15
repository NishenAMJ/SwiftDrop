# Tracking Report Calculations

These definitions are the contract for Tracking & Reporting report endpoints. Dates are interpreted
in UTC and both `startDate` and `endDate` are inclusive.

## Booking cohort

When date filters are supplied, summary and status reports operate on the distinct parcels whose
`parcel.booked` event occurred in that period. Missing filters are open-ended. A start date after the
end date is invalid.

## Summary values

- **Total parcels booked:** distinct parcels in the booking cohort.
- **Parcels currently in transit:** cohort parcels whose current projection status is `IN_TRANSIT`.
- **Delivered parcels:** cohort parcels whose current projection status is `DELIVERED`.
- **Failed deliveries:** cohort parcels whose current projection status is `DELIVERY_FAILED`.
- **Delivery success rate:** `delivered / (delivered + failed) * 100`, rounded to two decimal places.
  Active parcels are not included in the denominator. The result is `0.00` when there are no
  terminal outcomes.
- **Status totals:** cohort parcels grouped by their current projection status.

A parcel that previously failed but was later delivered is counted as delivered because these are
current-state reports.

## Delivery counts

- **Daily delivery counts:** distinct parcels with a `delivery.delivered` event in each UTC calendar
  day inside the selected delivery-event period.
- **Monthly delivery counts:** the same calculation grouped by UTC calendar month. The period value
  is the first date of that month.
- Days or months with no deliveries are omitted.
