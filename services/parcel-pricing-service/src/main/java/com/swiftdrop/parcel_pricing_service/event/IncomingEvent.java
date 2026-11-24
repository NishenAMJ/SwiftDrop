package com.swiftdrop.parcel_pricing_service.event;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

/** A parsed shared-envelope message. Anything malformed is rejected to the dead-letter queue. */
record IncomingEvent(UUID eventId, String eventType, String producer, JsonNode data) {

    static IncomingEvent parse(byte[] body, JsonMapper json) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("Unreadable event JSON", ex);
        }
        UUID eventId = uuid(root.path("eventId"));
        String eventType = text(root.path("eventType"));
        JsonNode data = root.path("data");
        if (eventId == null || eventType == null || !data.isObject() || root.path("version").asInt(0) != 1) {
            throw new AmqpRejectAndDontRequeueException("Event does not match the shared envelope v1");
        }
        return new IncomingEvent(eventId, eventType, text(root.path("producer")), data);
    }

    String text(String field) {
        return text(data.path(field));
    }

    UUID uuid(String field) {
        return uuid(data.path(field));
    }

    BigDecimal decimal(String field) {
        JsonNode node = data.path(field);
        return node.isNumber() ? node.decimalValue() : null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.asString();
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuid(JsonNode node) {
        String value = text(node);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
