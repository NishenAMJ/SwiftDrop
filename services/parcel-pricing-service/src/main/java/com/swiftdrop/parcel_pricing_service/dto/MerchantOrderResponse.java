package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.MerchantOrder;
import com.swiftdrop.parcel_pricing_service.entity.OrderAddress;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.OrderStatus;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/** Merchant order as returned by the API. The JPA entity is never exposed directly. */
public record MerchantOrderResponse(
        Long id,
        String orderNumber,
        Long merchantId,
        String merchantReference,
        OrderContactDto sender,
        OrderContactDto receiver,
        OrderAddressDto pickupAddress,
        OrderAddressDto deliveryAddress,
        String parcelDescription,
        BigDecimal weightKg,
        DeliveryType deliveryType,
        String urgentNote,
        PaymentMethod paymentMethod,
        BigDecimal codAmount,
        BigDecimal deliveryFee,
        OrderStatus status,
        OrderSource source,
        Instant createdAt,
        Instant updatedAt
) {

    public static MerchantOrderResponse from(MerchantOrder order) {
        return new MerchantOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getMerchantId(),
                order.getMerchantReference(),
                new OrderContactDto(order.getSenderName(), order.getSenderPhone(), order.getSenderEmail()),
                new OrderContactDto(order.getReceiverName(), order.getReceiverPhone(), order.getReceiverEmail()),
                toDto(order.getPickupAddress()),
                toDto(order.getDeliveryAddress()),
                order.getParcelDescription(),
                order.getWeightKg(),
                order.getDeliveryType(),
                order.getUrgentNote(),
                order.getPaymentMethod(),
                order.getCodAmount(),
                order.getDeliveryFee(),
                order.getStatus(),
                order.getSource(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static OrderAddressDto toDto(OrderAddress address) {
        return new OrderAddressDto(address.getAddressLine1(), address.getAddressLine2(),
                address.getCity(), address.getDistrict(), address.getPostalCode());
    }
}
