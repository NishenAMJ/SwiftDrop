package com.swiftdrop.identity.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.swiftdrop.identity.user.Validation;

public record MerchantApplicationRequest(
        @NotBlank @Size(min = 2, max = 120) String businessName,
        @NotNull BusinessType businessType,
        @Size(max = 50) String businessRegistrationNumber,
        @Email @Size(max = 254) String contactEmail,
        @NotNull @Pattern(regexp = Validation.PHONE_E164, message = Validation.PHONE_MESSAGE) String contactPhone,
        @NotNull @Valid AddressDto businessAddress) {

    @AssertTrue(message = "is required for partnerships and companies")
    public boolean isBusinessRegistrationNumber() {
        return businessType == null || !businessType.requiresRegistrationNumber()
                || (businessRegistrationNumber != null && !businessRegistrationNumber.isBlank());
    }
}
