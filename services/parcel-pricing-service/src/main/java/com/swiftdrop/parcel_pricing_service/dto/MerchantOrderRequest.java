package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for creating a merchant order, shared by the dashboard and the external API.
 *
 * <p>Deliberately has no merchant id, fee, status or order number: the merchant comes
 * from authentication, the fee from the pricing logic, and the rest from the server.
 */
public record MerchantOrderRequest(

        /** The merchant's own order number. Required for external API orders (duplicate protection). */
        @Size(max = 100, message = "Merchant reference must be at most 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9._/-]*$",
                message = "Merchant reference may contain letters, digits, '.', '_', '/' and '-' only")
        String merchantReference,

        @NotNull(message = "Sender details are required")
        @Valid
        OrderContactDto sender,

        @NotNull(message = "Receiver details are required")
        @Valid
        OrderContactDto receiver,

        @NotNull(message = "Pickup address is required")
        @Valid
        OrderAddressDto pickupAddress,

        @NotNull(message = "Delivery address is required")
        @Valid
        OrderAddressDto deliveryAddress,

        @NotBlank(message = "Parcel description is required")
        @Size(max = 500, message = "Parcel description must be at most 500 characters")
        String parcelDescription,

        @NotNull(message = "Parcel weight is required")
        @DecimalMin(value = "0.01", message = "Parcel weight must be between 0.01 and 50.00 kg")
        @DecimalMax(value = "50.00", message = "Parcel weight must be between 0.01 and 50.00 kg")
        @Digits(integer = 3, fraction = 2, message = "Parcel weight may have at most 2 decimal places")
        BigDecimal weightKg,

        @NotNull(message = "Delivery type is required (NORMAL or URGENT)")
        DeliveryType deliveryType,

        /** Only kept for URGENT orders. */
        @Size(max = 255, message = "Urgent note must be at most 255 characters")
        String urgentNote,

        @NotNull(message = "Payment method is required (ONLINE or COD)")
        PaymentMethod paymentMethod,

        /** Required for COD, must be absent for ONLINE. */
        @DecimalMin(value = "0.01", message = "COD amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "COD amount may have at most 2 decimal places")
        BigDecimal codAmount
) {
}
