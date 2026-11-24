package com.swiftdrop.delivery.courier.domain;

import java.util.List;

public enum AssignmentStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    /** The booking was cancelled in Parcel & Pricing. Set only by the cancellation event, never by a person. */
    CANCELLED;

    public static final List<AssignmentStatus> ACTIVE = List.of(ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY);

    public boolean canMoveTo(AssignmentStatus next) {
        return switch (this) {
            case ASSIGNED -> next == PICKED_UP || next == CANCELLED;
            case PICKED_UP -> next == IN_TRANSIT || next == OUT_FOR_DELIVERY || next == CANCELLED;
            case IN_TRANSIT -> next == OUT_FOR_DELIVERY || next == CANCELLED;
            case OUT_FOR_DELIVERY -> next == DELIVERED || next == DELIVERY_FAILED || next == CANCELLED;
            case DELIVERED, DELIVERY_FAILED, CANCELLED -> false;
        };
    }
}
