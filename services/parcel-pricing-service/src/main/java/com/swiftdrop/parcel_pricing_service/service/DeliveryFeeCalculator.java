package com.swiftdrop.parcel_pricing_service.service;

import java.math.BigDecimal;

/**
 * Contract for calculating delivery fees.
 *
 * Implementations must return a non-negative
 * delivery fee with two decimal places.
 */
public interface DeliveryFeeCalculator {

    BigDecimal calculateFee(DeliveryFeeRequest request);
}