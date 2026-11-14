package com.swiftdrop.tracking.event;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.swiftdrop.tracking.common.InvalidTrackingEventException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Component
public class TrackingEventValidator {

    private static final String PARCEL_PRODUCER = "parcel-pricing-service";
    private static final String DELIVERY_PRODUCER = "delivery-operations-service";

    private final Validator validator;

    public TrackingEventValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(EventEnvelope event, EventSource source) {
        Set<ConstraintViolation<EventEnvelope>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new InvalidTrackingEventException("Event does not satisfy the tracking contract");
        }
        if (event.version() != 1) {
            throw new InvalidTrackingEventException("Unsupported event version");
        }

        switch (event.eventType()) {
            case "parcel.booked" -> require(source == EventSource.PARCEL_PRICING
                    && PARCEL_PRODUCER.equals(event.producer())
                    && "BOOKED".equals(event.data().deliveryStatus()), "Invalid parcel.booked event");
            case "parcel.received-at-warehouse" -> {
                requireDeliveryEvent(event, source, "AT_WAREHOUSE");
                require(event.data().warehouseId() != null && event.data().publicLocation() != null,
                        "Warehouse receipt fields are missing");
            }
            case "parcel.transferred" -> {
                requireDeliveryEvent(event, source, "IN_TRANSIT");
                require(event.data().transferId() != null
                                && event.data().sourceWarehouseId() != null
                                && event.data().destinationWarehouseId() != null
                                && event.data().publicLocation() != null,
                        "Transfer fields are missing");
            }
            case "delivery.assigned" -> {
                requireDeliveryEvent(event, source, "ASSIGNED");
                require(event.data().assignmentId() != null && event.data().courierId() != null,
                        "Assignment fields are missing");
            }
            case "delivery.picked-up" -> requireDeliveryEvent(event, source, "PICKED_UP");
            case "delivery.in-transit" -> requireDeliveryEvent(event, source, "IN_TRANSIT");
            case "delivery.out-for-delivery" -> requireDeliveryEvent(event, source, "OUT_FOR_DELIVERY");
            case "delivery.delivered" -> requireDeliveryEvent(event, source, "DELIVERED");
            case "delivery.failed" -> {
                requireDeliveryEvent(event, source, "DELIVERY_FAILED");
                require(event.data().attemptId() != null && event.data().failureCode() != null,
                        "Delivery failure fields are missing");
            }
            default -> throw new InvalidTrackingEventException("Unsupported tracking event type");
        }
    }

    private void requireDeliveryEvent(EventEnvelope event, EventSource source, String expectedStatus) {
        require(source == EventSource.DELIVERY_OPERATIONS
                        && DELIVERY_PRODUCER.equals(event.producer())
                        && expectedStatus.equals(event.data().deliveryStatus()),
                "Invalid delivery operations event");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidTrackingEventException(message);
        }
    }
}
