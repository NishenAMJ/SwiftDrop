package com.swiftdrop.identity.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BusinessAddress {

    @Column(name = "address_line1", nullable = false, length = 120)
    private String line1;

    @Column(name = "address_line2", length = 120)
    private String line2;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    protected BusinessAddress() {
    }

    static BusinessAddress from(AddressDto dto) {
        BusinessAddress address = new BusinessAddress();
        address.line1 = dto.line1().trim();
        address.line2 = blankToNull(dto.line2());
        address.city = dto.city().trim();
        address.postalCode = blankToNull(dto.postalCode());
        return address;
    }

    AddressDto toDto() {
        return new AddressDto(line1, line2, city, postalCode);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
