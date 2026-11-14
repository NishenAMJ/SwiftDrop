package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.OrderAddress;

import java.math.BigDecimal;

/** What the pricing logic needs to price one order. */
public record DeliveryFeeRequest(
        OrderAddress pickupAddress,
        OrderAddress deliveryAddress,
        BigDecimal weightKg,
        DeliveryType deliveryType
) {
}
