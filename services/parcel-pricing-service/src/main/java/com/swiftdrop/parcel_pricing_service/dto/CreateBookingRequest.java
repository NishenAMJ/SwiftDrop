package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateBookingRequest(
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    String description,

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be greater than 0 kg")
    BigDecimal weightKg,

    @NotBlank(message = "Urgency is required (NORMAL or URGENT)")
    String urgency,

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
