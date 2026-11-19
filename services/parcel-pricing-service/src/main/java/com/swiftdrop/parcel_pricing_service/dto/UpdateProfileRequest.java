package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    String email,

    @Size(max = 25, message = "Phone number cannot exceed 25 characters")
    String phoneNumber,

    @Size(max = 255, message = "Street address cannot exceed 255 characters")
    String streetAddress,

    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    String postalCode,

    String avatarBase64
) {}

