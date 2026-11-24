package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ParcelStatusServiceTest {

    private final ParcelRepository repo = mock(ParcelRepository.class);
    private final ParcelEventPublisher events = mock(ParcelEventPublisher.class);
    private final ParcelStatusService service = new ParcelStatusService(repo, events);

    private Parcel parcel(String status) {
        Parcel p = new Parcel();
        p.setStatus(status);
        p.setTrackingNumber("SWD-2026-000010");
        when(repo.lockById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void deliveryEventsMoveTheBookingForward() {
        Parcel p = parcel("BOOKED");
        for (String type : new String[]{"delivery.assigned", "delivery.picked-up", "parcel.received-at-warehouse",
                "parcel.transferred", "delivery.out-for-delivery", "delivery.delivered"}) {
            String status = ParcelStatusService.statusFor(type).orElseThrow();
            assertTrue(service.applyDeliveryUpdate(p.getId(), null, status, null), type);
        }
        assertEquals("DELIVERED", p.getStatus());
        verify(events, times(6)).statusChanged(eq(p), anyString(), anyString(), any());
    }

    @Test
    void lateOlderEventDoesNotMoveTheBookingBack() {
        Parcel p = parcel("OUT_FOR_DELIVERY");
        assertFalse(service.applyDeliveryUpdate(p.getId(), null, "PICKED_UP", null));
        assertEquals("OUT_FOR_DELIVERY", p.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void failedAttemptCanBeRetried() {
        Parcel p = parcel("DELIVERY_FAILED");
        assertTrue(service.applyDeliveryUpdate(p.getId(), null, "OUT_FOR_DELIVERY", null));
        assertTrue(service.applyDeliveryUpdate(p.getId(), null, "DELIVERED", null));
    }

    @Test
    void cancelledAndUnpaidBookingsAreNeverMoved() {
        Parcel cancelled = parcel("CANCELLED");
        Parcel draft = parcel("CHECKOUT_DRAFT");
        assertFalse(service.applyDeliveryUpdate(cancelled.getId(), null, "ASSIGNED", null));
        assertFalse(service.applyDeliveryUpdate(draft.getId(), null, "ASSIGNED", null));
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("CHECKOUT_DRAFT", draft.getStatus());
    }

    @Test
    void parcelCanBeFoundByTrackingNumber() {
        Parcel p = new Parcel();
        p.setStatus("BOOKED");
        when(repo.lockById(any())).thenReturn(Optional.empty());
        when(repo.lockByTrackingNumber("SWD-2026-000011")).thenReturn(Optional.of(p));
        assertTrue(service.applyDeliveryUpdate(UUID.randomUUID(), "SWD-2026-000011", "ASSIGNED", null));
    }

    @Test
    void deliveredIsFinal() {
        assertFalse(BookingStatus.canMove("DELIVERED", "DELIVERY_FAILED"));
        assertFalse(BookingStatus.canMove("BOOKED", "BOOKED"));
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
