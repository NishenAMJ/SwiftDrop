package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A customer booking. The weight limit of the active pricing rule (50 kg by default) is checked
 * by PricingService; the limits here only stop values the database cannot store.
 */
public record CreateBookingRequest(
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    String description,

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be greater than 0 kg")
    @DecimalMax(value = "1000.00", message = "Weight cannot exceed 1000 kg")
    @Digits(integer = 4, fraction = 2, message = "Weight can have at most 2 decimal places")
    BigDecimal weightKg,

    @Pattern(regexp = "(?i)NORMAL|URGENT", message = "Urgency must be NORMAL or URGENT")
    String urgency,

    @Pattern(regexp = "(?i)ONLINE|COD", message = "Payment method must be ONLINE or COD")
    String paymentMethod,

    @NotNull(message = "Pickup address is required")
    @Valid
    AddressDto pickupAddress,

    @NotNull(message = "Delivery address is required")
    @Valid
    AddressDto deliveryAddress
) {
    public CreateBookingRequest(String description, BigDecimal weightKg, String urgency, AddressDto pickupAddress, AddressDto deliveryAddress) {
        this(description, weightKg, urgency, "ONLINE", pickupAddress, deliveryAddress);
    }
}
