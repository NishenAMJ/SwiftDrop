package com.swiftdrop.identity.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The {@code Address} schema from the contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressDto(
        @NotBlank @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 20) String postalCode) {
}
