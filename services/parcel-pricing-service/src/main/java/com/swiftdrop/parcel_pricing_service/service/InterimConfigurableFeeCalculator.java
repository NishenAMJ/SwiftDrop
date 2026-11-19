package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * INTERIM stand-in for the Pricing Module: {@code base + perKg * weight (+ urgent surcharge)}.
 * It is only created when {@code swiftdrop.orders.fee.mode=interim} is set explicitly, and its
 * three rates have no defaults, so no fee is ever invented silently. Replace it by a
 * {@link DeliveryFeeCalculator} that delegates to the real pricing logic (zone and rate-card
 * based) and remove the property.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.orders.fee.mode", havingValue = "interim")
public class InterimConfigurableFeeCalculator implements DeliveryFeeCalculator {

    private final BigDecimal baseFee;
    private final BigDecimal perKgFee;
    private final BigDecimal urgentSurcharge;

    public InterimConfigurableFeeCalculator(
            @Value("${swiftdrop.orders.fee.base}") BigDecimal baseFee,
            @Value("${swiftdrop.orders.fee.per-kg}") BigDecimal perKgFee,
            @Value("${swiftdrop.orders.fee.urgent-surcharge}") BigDecimal urgentSurcharge) {
        this.baseFee = baseFee;
        this.perKgFee = perKgFee;
        this.urgentSurcharge = urgentSurcharge;
    }

    @Override
    public BigDecimal calculateFee(DeliveryFeeRequest request) {
        BigDecimal fee = baseFee.add(perKgFee.multiply(request.weightKg()));
        if (request.deliveryType() == DeliveryType.URGENT) {
            fee = fee.add(urgentSurcharge);
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
}
