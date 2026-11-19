package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.dto.OrderAddressDto;
import com.swiftdrop.parcel_pricing_service.dto.OrderContactDto;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.OrderStatus;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/** Shared fixtures for the merchant order controller tests. */
final class MerchantOrderTestData {

    private MerchantOrderTestData() {
    }

    /** A valid request body; {@code weight} is inserted as a raw JSON number so tests can break it. */
    static String validJson(String weight) {
        return """
                {
                  "merchantReference": "SHOP-1001",
                  "sender": {"name": "Nimal Perera", "phone": "+94771234567", "email": "nimal@shop.lk"},
                  "receiver": {"name": "Kamal Silva", "phone": "0712345678"},
                  "pickupAddress": {"addressLine1": "12 Galle Road", "city": "Colombo", "district": "Colombo", "postalCode": "00300"},
                  "deliveryAddress": {"addressLine1": "45 Temple Road", "city": "Kandy", "district": "Kandy", "postalCode": "20000"},
                  "parcelDescription": "Phone case",
                  "weightKg": %s,
                  "deliveryType": "NORMAL",
                  "paymentMethod": "COD",
                  "codAmount": 2500.00
                }
                """.formatted(weight);
    }

    static MerchantOrderResponse sampleResponse() {
        Instant now = Instant.parse("2026-11-14T06:30:00Z");
        return new MerchantOrderResponse(
                10L, "MO-00000010", 7L, "SHOP-1001",
                new OrderContactDto("Nimal Perera", "+94771234567", "nimal@shop.lk"),
                new OrderContactDto("Kamal Silva", "0712345678", null),
                new OrderAddressDto("12 Galle Road", null, "Colombo", "Colombo", "00300"),
                new OrderAddressDto("45 Temple Road", null, "Kandy", "Kandy", "20000"),
                "Phone case",
                new BigDecimal("2.50"),
                DeliveryType.NORMAL,
                null,
                PaymentMethod.COD,
                new BigDecimal("2500.00"),
                new BigDecimal("450.00"),
                OrderStatus.CREATED,
                OrderSource.DASHBOARD,
                now,
                now);
    }
}
