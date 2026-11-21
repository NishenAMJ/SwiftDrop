package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Profile update. Name and phone are saved in Identity (their owner). Email cannot be changed
 * here because it is the login of the account; any email sent is ignored.
 * The avatar is limited to about 500 KB and must be a PNG, JPEG or WebP data URL.
 */
public record UpdateProfileRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    String fullName,

    String email,

    @Size(max = 25, message = "Phone number cannot exceed 25 characters")
    @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{8,23}$", message = "Enter a valid phone number, for example 0771234567 or +94771234567")
    String phoneNumber,

    @Size(max = 255, message = "Street address cannot exceed 255 characters")
    String streetAddress,

    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    String postalCode,

    @Size(max = 700_000, message = "The profile photo must be smaller than 500 KB")
    @Pattern(regexp = "^$|^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$",
             message = "The profile photo must be a PNG, JPEG or WebP image")
    String avatarBase64
) {}
