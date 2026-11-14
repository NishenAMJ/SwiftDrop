package com.swiftdrop.parcel_pricing_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentContextResponse(
    String bookingId,
    String channel,
    UUID customerId,
    UUID merchantId,
    boolean payable,
    String pricingSnapshotId,
    BigDecimal amount,
    String currency,
    PayerDetailsDto payer
) {
    public record PayerDetailsDto(
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String country
    ) {}
}
