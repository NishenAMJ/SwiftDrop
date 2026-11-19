package com.swiftdrop.parcel_pricing_service.entity;

/**
 * Order lifecycle, taken from the parcel status flow in the SwiftDrop use case
 * diagram (Picked Up > In Transit > Out for Delivery > Delivered).
 * This module only ever creates orders in {@link #CREATED}; the later states are
 * set by the courier/dispatcher features.
 *
 * <p>If the service already has an order/parcel status enum, replace this one with it.
 */
public enum OrderStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED
}
