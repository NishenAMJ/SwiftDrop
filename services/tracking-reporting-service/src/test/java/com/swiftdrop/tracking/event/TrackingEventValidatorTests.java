package com.swiftdrop.tracking.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.tracking.common.InvalidTrackingEventException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TrackingEventValidatorTests {

    private TrackingEventValidator eventValidator;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        eventValidator = new TrackingEventValidator(validator);
    }

    @Test
    void acceptsAValidParcelBookedEvent() {
        EventEnvelope event = parcelBookedEvent();

        assertThatCode(() -> eventValidator.validate(event, EventSource.PARCEL_PRICING))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEventFromTheWrongQueue() {
        EventEnvelope event = parcelBookedEvent();

        assertThatThrownBy(() -> eventValidator.validate(event, EventSource.DELIVERY_OPERATIONS))
                .isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    void rejectsAMalformedTrackingNumber() {
        TrackingEventData invalidData = new TrackingEventData(
                UUID.randomUUID(),
                "PRIVATE-123",
                "BOOKED",
                "Parcel booking confirmed",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "parcel.booked",
                1,
                Instant.parse("2026-11-14T10:00:00Z"),
                "parcel-pricing-service",
                invalidData);

        assertThatThrownBy(() -> eventValidator.validate(event, EventSource.PARCEL_PRICING))
                .isInstanceOf(InvalidTrackingEventException.class);
    }

    private EventEnvelope parcelBookedEvent() {
        TrackingEventData data = new TrackingEventData(
                UUID.randomUUID(),
                "SWD-2026-100001",
                "BOOKED",
                "Parcel booking confirmed",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new EventEnvelope(
                UUID.randomUUID(),
                "parcel.booked",
                1,
                Instant.parse("2026-11-14T10:00:00Z"),
                "parcel-pricing-service",
                data);
    }
}
