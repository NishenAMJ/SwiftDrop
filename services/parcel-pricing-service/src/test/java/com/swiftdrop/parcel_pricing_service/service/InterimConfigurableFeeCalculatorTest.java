package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InterimConfigurableFeeCalculatorTest {

    // Test rates only; the real rates belong to the Pricing Module.
    private final InterimConfigurableFeeCalculator calculator = new InterimConfigurableFeeCalculator(
            new BigDecimal("300.00"), new BigDecimal("50.00"), new BigDecimal("200.00"));

    @Test
    void normalDelivery_isBasePlusWeightCharge() {
        BigDecimal fee = calculator.calculateFee(
                new DeliveryFeeRequest(null, null, new BigDecimal("2.50"), DeliveryType.NORMAL));

        assertThat(fee).isEqualTo(new BigDecimal("425.00"));
    }

    @Test
    void urgentDelivery_addsSurcharge() {
        BigDecimal fee = calculator.calculateFee(
                new DeliveryFeeRequest(null, null, new BigDecimal("2.50"), DeliveryType.URGENT));

        assertThat(fee).isEqualTo(new BigDecimal("625.00"));
    }

    @Test
    void fee_isRoundedToTwoDecimals() {
        InterimConfigurableFeeCalculator rounding = new InterimConfigurableFeeCalculator(
                new BigDecimal("0.00"), new BigDecimal("0.333"), new BigDecimal("0.00"));

        BigDecimal fee = rounding.calculateFee(
                new DeliveryFeeRequest(null, null, new BigDecimal("1.00"), DeliveryType.NORMAL));

        assertThat(fee).isEqualTo(new BigDecimal("0.33"));
    }
}
