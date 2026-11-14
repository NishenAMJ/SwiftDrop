package com.swiftdrop.parcel_pricing_service.entity;

import jakarta.persistence.Embeddable;

/**
 * Address stored inside an order. Used twice in {@link MerchantOrder} (pickup and
 * delivery) with different column prefixes.
 */
@Embeddable
public class OrderAddress {

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String postalCode;

    protected OrderAddress() {
        // required by JPA
    }

    public OrderAddress(String addressLine1, String addressLine2, String city,
                        String district, String postalCode) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.district = district;
        this.postalCode = postalCode;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    public String getPostalCode() {
        return postalCode;
    }
}
