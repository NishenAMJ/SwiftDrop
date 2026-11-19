package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Name and contact details of a sender or receiver. */
public record OrderContactDto(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N} .,'&()/-]*$", message = "Name contains unsupported characters")
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{6,19}$", message = "Phone number is not valid")
        String phone,

        @Email(message = "Email address is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email
) {
}
