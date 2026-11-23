package com.swiftdrop.payment.onlinepayment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record CancellationEvent(UUID eventId, UUID bookingId, UUID paymentId, UUID customerId,
        String method, BigDecimal fee, BigDecimal target) {
    public static CancellationEvent parse(String body, JsonMapper json) {
        try { return parseRecord(body, json); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid cancellation payload", invalid); }
    }
    private static CancellationEvent parseRecord(String body, JsonMapper json) {
        JsonNode root = json.readTree(body);
        if (!"parcel.cancelled".equals(root.path("eventType").asString())
                || !"parcel-pricing-service".equals(root.path("producer").asString())
                || !root.path("version").isIntegralNumber() || root.path("version").asInt() != 1) throw new IllegalArgumentException("Invalid cancellation envelope");
        Instant.parse(root.path("occurredAt").asString());
        JsonNode data = root.path("data");
        Instant.parse(data.path("cancelledAt").asString());
        if (!"CANCELLED".equals(data.path("deliveryStatus").asString())) throw new IllegalArgumentException("Invalid cancellation status");
        String method = data.path("paymentMethod").asString();
        if (!"ONLINE".equals(method) && !"COD".equals(method)) throw new IllegalArgumentException("Invalid payment method");
        BigDecimal fee = amount(data, "cancellationFee");
        BigDecimal target = amount(data, "refundAmount");
        UUID payment = data.hasNonNull("paymentId") ? UUID.fromString(data.path("paymentId").asString()) : null;
        if ("ONLINE".equals(method) && payment == null) throw new IllegalArgumentException("Missing payment");
        if ("COD".equals(method) && (fee.signum() != 0 || target.signum() != 0)) throw new IllegalArgumentException("COD cannot refund money");
        if ("COD".equals(method) && payment != null) throw new IllegalArgumentException("COD cannot reference an online payment");
        return new CancellationEvent(UUID.fromString(root.path("eventId").asString()),
                UUID.fromString(data.path("parcelId").asString()), payment,
                UUID.fromString(data.path("customerUserId").asString()), method, fee, target);
    }
    private static BigDecimal amount(JsonNode data, String field) {
        JsonNode node = data.path(field);
        if (!node.isNumber()) throw new IllegalArgumentException("Missing cancellation amount");
        BigDecimal amount = node.decimalValue();
        if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("9999999999.99")) > 0) throw new IllegalArgumentException("Invalid cancellation amount");
        return amount.setScale(2);
    }
    public String fingerprint() {
        return bookingId + ":" + paymentId + ":" + customerId + ":" + method + ":" + fee + ":" + target;
    }
}
