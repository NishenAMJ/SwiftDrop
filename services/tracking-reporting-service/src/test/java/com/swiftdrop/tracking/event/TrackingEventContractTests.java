package com.swiftdrop.tracking.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.tracking.common.InvalidTrackingEventException;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every tracking event type against the shared contract: the right queue, producer and delivery
 * status, plus the type-specific fields. Anything else must be rejected, never projected.
 */
class TrackingEventContractTests {
    private static final String DELIVERY = "delivery-operations-service";
    private static final UUID ID = UUID.fromString("6c0d1c1e-3a52-4c8e-9f0c-1f2a3b4c5d6e");

    private final TrackingEventValidator validator =
            new TrackingEventValidator(Validation.buildDefaultValidatorFactory().getValidator());

    /** A delivery-operations event carrying every optional field, so each type is valid by default. */
    private static EventEnvelope event(String type, String status, String producer, int version) {
        return new EventEnvelope(UUID.randomUUID(), type, version, Instant.parse("2026-11-14T10:00:00Z"), producer,
                new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", status, "Public update", "Colombo hub",
                        ID, ID, ID, ID, ID, ID, ID, "RECIPIENT_UNAVAILABLE"));
    }

    private static EventEnvelope withData(String type, String status, TrackingEventData data) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.parse("2026-11-14T10:00:00Z"), DELIVERY, data);
    }

    @ParameterizedTest
    @CsvSource({
            "parcel.received-at-warehouse,AT_WAREHOUSE",
            "parcel.transferred,IN_TRANSIT",
            "delivery.assigned,ASSIGNED",
            "delivery.picked-up,PICKED_UP",
            "delivery.in-transit,IN_TRANSIT",
            "delivery.out-for-delivery,OUT_FOR_DELIVERY",
            "delivery.delivered,DELIVERED",
            "delivery.failed,DELIVERY_FAILED"})
    void acceptsEachDeliveryOperationsEventWithItsStatus(String type, String status) {
        assertThatCode(() -> validator.validate(event(type, status, DELIVERY, 1), EventSource.DELIVERY_OPERATIONS))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "delivery.delivered,OUT_FOR_DELIVERY",
            "delivery.picked-up,DELIVERED",
            "delivery.assigned,BOOKED",
            "delivery.failed,DELIVERED",
            "parcel.transferred,AT_WAREHOUSE"})
    void rejectsAStatusThatDoesNotMatchTheEventType(String type, String status) {
        assertThatThrownBy(() -> validator.validate(event(type, status, DELIVERY, 1), EventSource.DELIVERY_OPERATIONS))
                .isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    void rejectsDeliveryEventsFromTheParcelQueueOrAnotherProducer() {
        assertThatThrownBy(() -> validator.validate(event("delivery.delivered", "DELIVERED", DELIVERY, 1), EventSource.PARCEL_PRICING))
                .isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> validator.validate(event("delivery.delivered", "DELIVERED", "parcel-pricing-service", 1),
                EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    void rejectsParcelBookedFromTheWrongProducerOrWithTheWrongStatus() {
        assertThatThrownBy(() -> validator.validate(event("parcel.booked", "BOOKED", DELIVERY, 1), EventSource.PARCEL_PRICING))
                .isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> validator.validate(event("parcel.booked", "ASSIGNED", "parcel-pricing-service", 1),
                EventSource.PARCEL_PRICING)).isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    void rejectsUnsupportedVersionsAndTypes() {
        assertThatThrownBy(() -> validator.validate(event("delivery.delivered", "DELIVERED", DELIVERY, 2), EventSource.DELIVERY_OPERATIONS))
                .isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> validator.validate(event("delivery.teleported", "DELIVERED", DELIVERY, 1), EventSource.DELIVERY_OPERATIONS))
                .isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    void warehouseReceiptNeedsWarehouseAndPublicLocation() {
        var noWarehouse = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "AT_WAREHOUSE", "Arrived", "Hub",
                null, null, null, null, null, null, null, null);
        var noLocation = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "AT_WAREHOUSE", "Arrived", null,
                ID, null, null, null, null, null, null, null);
        for (var data : new TrackingEventData[]{noWarehouse, noLocation}) {
            assertThatThrownBy(() -> validator.validate(withData("parcel.received-at-warehouse", "AT_WAREHOUSE", data),
                    EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
        }
    }

    @Test
    void transferNeedsBothWarehousesTheTransferAndALocation() {
        TrackingEventData[] incomplete = {
                new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "IN_TRANSIT", "Moving", "Road", null, null, ID, ID, null, null, null, null),
                new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "IN_TRANSIT", "Moving", "Road", null, ID, null, ID, null, null, null, null),
                new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "IN_TRANSIT", "Moving", "Road", null, ID, ID, null, null, null, null, null),
                new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "IN_TRANSIT", "Moving", null, null, ID, ID, ID, null, null, null, null)};
        for (var data : incomplete) {
            assertThatThrownBy(() -> validator.validate(withData("parcel.transferred", "IN_TRANSIT", data),
                    EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
        }
    }

    @Test
    void assignmentNeedsTheAssignmentAndCourier() {
        var noCourier = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "ASSIGNED", "Assigned", null,
                null, null, null, null, ID, null, null, null);
        var noAssignment = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "ASSIGNED", "Assigned", null,
                null, null, null, null, null, ID, null, null);
        for (var data : new TrackingEventData[]{noCourier, noAssignment}) {
            assertThatThrownBy(() -> validator.validate(withData("delivery.assigned", "ASSIGNED", data),
                    EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
        }
    }

    @Test
    void failedDeliveryNeedsTheAttemptAndAFailureCode() {
        var noCode = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "DELIVERY_FAILED", "Failed", null,
                null, null, null, null, null, null, ID, null);
        var noAttempt = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "DELIVERY_FAILED", "Failed", null,
                null, null, null, null, null, null, null, "REFUSED");
        for (var data : new TrackingEventData[]{noCode, noAttempt}) {
            assertThatThrownBy(() -> validator.validate(withData("delivery.failed", "DELIVERY_FAILED", data),
                    EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
        }
    }

    @Test
    void failureCodesMustBeMachineReadable() {
        var lowercase = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "DELIVERY_FAILED", "Failed", null,
                null, null, null, null, null, null, ID, "refused by customer");
        assertThatThrownBy(() -> validator.validate(withData("delivery.failed", "DELIVERY_FAILED", lowercase),
                EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    void missingParcelReferenceIsRejected() {
        var noParcel = new TrackingEventData(null, "SWD-2026-100001", "DELIVERED", "Delivered", null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(withData("delivery.delivered", "DELIVERED", noParcel),
                EventSource.DELIVERY_OPERATIONS)).isInstanceOf(InvalidTrackingEventException.class);
    }
}
