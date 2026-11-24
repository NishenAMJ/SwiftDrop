package com.swiftdrop.delivery.courier.parcel;

import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/** Calls GET /api/v1/dispatch/parcels/track/{trackingNumber} on parcel-pricing-service (dispatcher token). */
@Component
@ConditionalOnProperty(name = "delivery.parcel.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpParcelLookup implements ParcelLookup {

    private final RestClient client;

    @Autowired
    public HttpParcelLookup(@Value("${delivery.parcel.base-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    HttpParcelLookup(RestClient client) {
        this.client = client;
    }

    @Override
    public ParcelRef find(String trackingNumber, String bearerToken) {
        try {
            var booking = client.get()
                    .uri("/api/v1/dispatch/parcels/track/{trackingNumber}", trackingNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(Booking.class);
            if (booking == null || booking.id() == null || booking.trackingNumber() == null) {
                throw new ParcelServiceUnavailableException("Parcel service returned an unreadable response", null);
            }
            return new ParcelRef(booking.id(), booking.trackingNumber(), booking.customerId(), booking.merchantId(),
                    booking.codAmount(), booking.pricingSnapshotId(), booking.currency(),
                    contact(booking.pickupAddress()), contact(booking.deliveryAddress()), booking.status(),
                    booking.weightKg());
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new ResourceNotFoundException("Parcel not found: " + trackingNumber);
        } catch (ResourceAccessException unreachable) {
            throw new ParcelServiceUnavailableException("Parcel service is not reachable", unreachable);
        } catch (RestClientResponseException rejected) {
            throw new ParcelServiceUnavailableException(
                    "Parcel service answered with status " + rejected.getStatusCode().value(), rejected);
        }
    }

    private static ParcelContact contact(Address address) {
        if (address == null) {
            return null;
        }
        var place = java.util.stream.Stream.of(address.streetAddress(), address.city(), address.postalCode())
                .filter(part -> part != null && !part.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        return new ParcelContact(address.contactName(), address.contactPhone(), place);
    }

    /** The only fields needed from the dispatch lookup response. */
    record Booking(UUID id, String trackingNumber, UUID customerId, UUID merchantId, BigDecimal codAmount,
                   String pricingSnapshotId, String currency, Address pickupAddress, Address deliveryAddress,
                   String status, BigDecimal weightKg) {
    }

    record Address(String contactName, String contactPhone, String streetAddress, String city, String postalCode) {
    }
}
