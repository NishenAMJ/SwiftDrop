package com.swiftdrop.payment.cod.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks a delivery.cod-collected event against Parcel & Pricing, which owns the booking amount.
 * Delivery Operations only carries copies, so nothing is posted to the ledger unless the booking, the
 * merchant, the pricing snapshot, the currency and the expected amount all match the authoritative record.
 *
 * <p>Enabled with PAYMENT_COD_VERIFICATION_MODE=parcel. There is no user token on a queue message, so the
 * call is authenticated with a shared key (PAYMENT_PARCEL_INTERNAL_KEY, the same value Parcel & Pricing has
 * as PARCEL_INTERNAL_API_KEY).
 *
 * <ul>
 *   <li>A booking that does not match is {@link InvalidCodEvent}: the message is dead-lettered.</li>
 *   <li>Parcel & Pricing being unreachable, refusing our key or failing is {@link CodVerificationUnavailable}:
 *       the message is retried and nothing is lost.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "payment.cod.verification-mode", havingValue = "parcel")
final class ParcelCodAmountVerifier implements CodAmountVerifier {

    private final JsonMapper json;
    private final String baseUrl;
    private final String key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    ParcelCodAmountVerifier(Environment environment, JsonMapper json) {
        this.json = json;
        this.baseUrl = environment.getRequiredProperty("payment.pricing.base-url");
        this.key = environment.getProperty("payment.cod.parcel-internal-key", "");
        if (key.isBlank()) {
            throw new IllegalStateException(
                "PAYMENT_COD_VERIFICATION_MODE=parcel requires PAYMENT_PARCEL_INTERNAL_KEY (the shared key Parcel & "
                    + "Pricing has as PARCEL_INTERNAL_API_KEY).");
        }
    }

    @Override
    public void verify(CodCollectedEvent.Data collection) {
        UUID bookingId;
        try {
            bookingId = UUID.fromString(collection.bookingId());
        } catch (IllegalArgumentException | NullPointerException notAnId) {
            throw new InvalidCodEvent("The COD event's bookingId is not a booking id.");
        }
        JsonNode booking = fetch(bookingId);

        require("COD".equals(text(booking, "paymentMethod")), "The booking is not a Cash on Delivery booking.");
        require(Objects.equals(collection.trackingNumber(), text(booking, "trackingNumber")),
            "The tracking number does not match the booking.");
        require(collection.merchantId() != null && collection.merchantId().toString().equals(text(booking, "merchantId")),
            "The merchant does not match the booking.");
        require(collection.pricingSnapshotId() != null
                && collection.pricingSnapshotId().equals(text(booking, "pricingSnapshotId")),
            "The pricing snapshot does not match the booking.");
        require(collection.currency() != null && collection.currency().equals(text(booking, "currency")),
            "The currency does not match the booking.");
        BigDecimal authoritative = booking.hasNonNull("codAmount") ? booking.get("codAmount").decimalValue() : null;
        require(authoritative != null && collection.expectedAmount() != null
                && authoritative.compareTo(collection.expectedAmount()) == 0,
            "The expected amount does not match the booking's COD amount.");
    }

    private JsonNode fetch(UUID bookingId) {
        String url = baseUrl + "/api/v1/internal/parcels/"
            + URLEncoder.encode(bookingId.toString(), StandardCharsets.UTF_8) + "/cod-verification";
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("X-Internal-Key", key).GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CodVerificationUnavailable("COD verification was interrupted.");
        } catch (Exception unreachable) {
            throw new CodVerificationUnavailable("Parcel & Pricing could not be reached to verify the COD amount.");
        }
        if (response.statusCode() == 404) {
            throw new InvalidCodEvent("The booking in the COD event does not exist.");
        }
        if (response.statusCode() != 200) {
            // Includes 401/403 (key not accepted) and 5xx: a setup or outage problem, so retry, never dead-letter.
            throw new CodVerificationUnavailable(
                "Parcel & Pricing answered the COD verification with status " + response.statusCode() + ".");
        }
        try {
            return json.readTree(response.body());
        } catch (Exception unreadable) {
            throw new CodVerificationUnavailable("Parcel & Pricing sent an unreadable COD verification.");
        }
    }

    @Override
    public UUID verifyCustomer(CodCollectedEvent.Data collection) {
        JsonNode booking = fetch(UUID.fromString(collection.bookingId()));
        require("DIRECT_WEBSITE".equals(text(booking, "channel")) && !booking.hasNonNull("merchantId")
                && "COD".equals(text(booking, "paymentMethod")), "Not a direct customer COD booking.");
        require(!"CANCELLED".equals(text(booking, "status")), "The booking was cancelled.");
        require(collection.trackingNumber().equals(text(booking, "trackingNumber"))
                && collection.pricingSnapshotId().equals(text(booking, "pricingSnapshotId"))
                && collection.currency().equals(text(booking, "currency"))
                && booking.path("codAmount").isNumber()
                && collection.expectedAmount().compareTo(booking.path("codAmount").decimalValue()) == 0,
                "Customer COD does not match the authoritative booking.");
        try { return UUID.fromString(text(booking, "customerId")); }
        catch (RuntimeException invalid) { throw new InvalidCodEvent("Missing customer on COD booking."); }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new InvalidCodEvent(message);
        }
    }
}
