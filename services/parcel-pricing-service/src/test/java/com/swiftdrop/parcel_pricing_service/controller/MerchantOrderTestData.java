package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.AddressDto;
import com.swiftdrop.parcel_pricing_service.dto.FeeBreakdownDto;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Shared fixtures for the merchant order tests. */
public final class MerchantOrderTestData {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MerchantOrderTestData() {
    }

    /** A JSON body accepted by both merchant order endpoints. */
    public static String validJson(String weightKg) {
        return """
                {
                  "merchantReference": "SHOP-1001",
                  "description": "Phone case",
                  "weightKg": %s,
                  "urgency": "NORMAL",
                  "paymentMethod": "COD",
                  "codAmount": 2500.00,
                  "pickupAddress": {"contactName": "Lanka Gadgets", "contactPhone": "0112345678",
                                    "streetAddress": "12 Galle Rd", "city": "Colombo", "zoneCode": "COLOMBO"},
                  "deliveryAddress": {"contactName": "Kamala Silva", "contactPhone": "0777654321",
                                      "streetAddress": "45 Lake Rd", "city": "Kandy", "zoneCode": "KANDY"}
                }
                """.formatted(weightKg);
    }

    public static AddressDto pickup() {
        return new AddressDto("Lanka Gadgets", "0112345678", "12 Galle Rd", "Colombo", null, "COLOMBO");
    }

    public static AddressDto delivery() {
        return new AddressDto("Kamala Silva", "0777654321", "45 Lake Rd", "Kandy", null, "KANDY");
    }

    public static MerchantOrderRequest codRequest(String reference) {
        return new MerchantOrderRequest(reference, "Phone case", new BigDecimal("2.50"), DeliveryType.NORMAL, null,
                PaymentMethod.COD, new BigDecimal("2500.00"), pickup(), delivery());
    }

    public static MerchantOrderRequest onlineRequest(String reference) {
        return new MerchantOrderRequest(reference, "Phone case", new BigDecimal("2.50"), DeliveryType.URGENT,
                "Before 5pm", PaymentMethod.ONLINE, null, pickup(), delivery());
    }

    public static MerchantOrderResponse sampleResponse() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-21T10:00:00Z");
        return new MerchantOrderResponse(ORDER_ID, "SWD-2026-000010", MERCHANT_ID, "SHOP-1001", "Phone case",
                new BigDecimal("2.50"), "NORMAL", null, "COD", "PENDING", new BigDecimal("2500.00"),
                new BigDecimal("575.00"), "LKR",
                new FeeBreakdownDto(new BigDecimal("300.00"), new BigDecimal("125.00"), new BigDecimal("150.00"),
                        "KANDY", BigDecimal.ZERO, new BigDecimal("575.00"), "STANDARD_DEFAULT"),
                "BOOKED", OrderSource.DASHBOARD, "RCP-2026-000010", now.plusHours(24), pickup(), delivery(), now, now);
    }

    /** A MERCHANT access token as Identity issues it (roles claim and merchant_id claim). */
    public static RequestPostProcessor merchantToken() {
        return jwt().jwt(j -> j.subject(USER_ID.toString())
                        .claim("roles", List.of("MERCHANT"))
                        .claim("merchant_id", MERCHANT_ID.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    public static RequestPostProcessor tokenWithRole(String role) {
        return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
