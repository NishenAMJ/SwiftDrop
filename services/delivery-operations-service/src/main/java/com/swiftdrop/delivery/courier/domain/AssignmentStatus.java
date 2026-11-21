package com.swiftdrop.delivery.courier.domain;

import java.util.List;

public enum AssignmentStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED;

    public static final List<AssignmentStatus> ACTIVE = List.of(ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY);

    public boolean canMoveTo(AssignmentStatus next) {
        return switch (this) {
            case ASSIGNED -> next == PICKED_UP;
            case PICKED_UP -> next == IN_TRANSIT || next == OUT_FOR_DELIVERY;
            case IN_TRANSIT -> next == OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == DELIVERED || next == DELIVERY_FAILED;
            case DELIVERED, DELIVERY_FAILED -> false;
        };
    }
}
