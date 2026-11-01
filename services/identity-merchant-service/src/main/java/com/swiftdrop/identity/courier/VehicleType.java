package com.swiftdrop.identity.courier;

public enum VehicleType {
    BICYCLE,
    MOTORCYCLE,
    THREE_WHEELER,
    CAR,
    VAN,
    TRUCK;

    /** Every vehicle except a bicycle needs a driving licence and a registration number. */
    public boolean requiresDocuments() {
        return this != BICYCLE;
    }
}
