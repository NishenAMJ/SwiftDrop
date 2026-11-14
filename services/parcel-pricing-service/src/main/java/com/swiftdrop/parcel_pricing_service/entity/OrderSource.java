package com.swiftdrop.parcel_pricing_service.entity;

/** Which channel created the order. Stored for auditing. */
public enum OrderSource {
    DASHBOARD,
    EXTERNAL_API
}
