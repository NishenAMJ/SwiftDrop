package com.swiftdrop.delivery.courier.parcel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Local and test stand-in for Parcel & Pricing (delivery.parcel.stub-enabled=true). Every tracking
 * number "exists"; the parcel id is derived from it, so the same number always gives the same id.
 */
@Component
@ConditionalOnProperty(name = "delivery.parcel.stub-enabled", havingValue = "true")
public class StubParcelLookup implements ParcelLookup {

    @Override
    public ParcelRef find(String trackingNumber, String bearerToken) {
        var number = trackingNumber.trim().toUpperCase(Locale.ROOT);
        return new ParcelRef(UUID.nameUUIDFromBytes(number.getBytes(StandardCharsets.UTF_8)), number,
                UUID.nameUUIDFromBytes(("customer:" + number).getBytes(StandardCharsets.UTF_8)));
    }
}
