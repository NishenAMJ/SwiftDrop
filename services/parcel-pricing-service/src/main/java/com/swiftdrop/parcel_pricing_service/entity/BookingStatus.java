package com.swiftdrop.parcel_pricing_service.entity;

import java.util.Map;
import java.util.Set;

/**
 * Booking lifecycle values stored in {@code parcels.status}.
 *
 * <p>Before payment: CHECKOUT_DRAFT, which becomes EXPIRED or ABANDONED if it is never paid.
 * After confirmation the status follows Delivery Operations events, using the same values as the
 * shared delivery-status contract so every dashboard and Tracking show the same word.
 */
public final class BookingStatus {

    public static final String CHECKOUT_DRAFT = "CHECKOUT_DRAFT";
    public static final String EXPIRED = "EXPIRED";
    public static final String ABANDONED = "ABANDONED";

    public static final String BOOKED = "BOOKED";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String PICKED_UP = "PICKED_UP";
    public static final String AT_WAREHOUSE = "AT_WAREHOUSE";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String DELIVERED = "DELIVERED";
    public static final String DELIVERY_FAILED = "DELIVERY_FAILED";
    public static final String CANCELLED = "CANCELLED";

    /** Drafts that were never paid. A late verified payment may still confirm them. */
    public static final Set<String> UNPAID_DRAFTS = Set.of(CHECKOUT_DRAFT, EXPIRED, ABANDONED);

    /** Statuses that come from Delivery Operations, ordered by how far the parcel has moved. */
    private static final Map<String, Integer> PROGRESS = Map.of(
            BOOKED, 0,
            ASSIGNED, 1,
            PICKED_UP, 2,
            AT_WAREHOUSE, 3,
            IN_TRANSIT, 4,
            OUT_FOR_DELIVERY, 5,
            DELIVERY_FAILED, 6,
            DELIVERED, 7);

    private BookingStatus() {
    }

    public static boolean isConfirmed(String status) {
        return status != null && PROGRESS.containsKey(status);
    }

    /**
     * Events are not guaranteed to arrive in order, so a late "picked up" must not move a parcel
     * that is already out for delivery backwards. A failed attempt can be retried, so a parcel
     * may leave DELIVERY_FAILED for any later delivery step.
     */
    public static boolean canMove(String from, String to) {
        if (!isConfirmed(from) || !isConfirmed(to) || DELIVERED.equals(from)) return false;
        if (DELIVERY_FAILED.equals(from)) return !BOOKED.equals(to) && !DELIVERY_FAILED.equals(to);
        return PROGRESS.get(to) > PROGRESS.get(from);
    }
}
