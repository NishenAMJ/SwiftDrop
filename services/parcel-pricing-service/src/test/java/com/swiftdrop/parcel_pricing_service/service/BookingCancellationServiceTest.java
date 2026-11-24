package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.CancellationResultDto;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingCancellationServiceTest {

    private final ParcelRepository repo = mock(ParcelRepository.class);
    private final ParcelEventPublisher events = mock(ParcelEventPublisher.class);
    private final BookingCancellationService service = new BookingCancellationService(repo, events, BigDecimal.ZERO);

    private Jwt jwt(UUID id) {
        return Jwt.withTokenValue("test").header("alg", "RS256").subject(id.toString()).build();
    }

    private Parcel parcel(String status, String method) {
        Parcel p = new Parcel();
        p.setCustomerId(UUID.randomUUID());
        p.setStatus(status);
        p.setPaymentMethod(method);
        p.setPaymentStatus("ONLINE".equals(method) ? "PAID" : "PENDING");
        p.setEstimatedFee(new BigDecimal("550.00"));
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        when(repo.lockById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void unauthenticatedIsRejectedBeforeLookup() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ApiException.class, () -> service.eligibility(UUID.randomUUID(), null)).getStatus());
        verifyNoInteractions(repo);
    }

    @Test
    void anotherCustomerIsRejected() {
        Parcel p = parcel("BOOKED", "COD");
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ApiException.class, () -> service.eligibility(p.getId(), jwt(UUID.randomUUID()))).getStatus());
    }

    @Test
    void bookedParcelWithoutCourierCanBeCancelled() {
        Parcel p = parcel("BOOKED", "COD");
        assertTrue(service.eligibility(p.getId(), jwt(p.getCustomerId())).eligible());

        CancellationResultDto result = service.initiate(p.getId(), jwt(p.getCustomerId()));
        assertTrue(result.completed());
        assertEquals("CANCELLED", p.getStatus());
        assertEquals("COMPLETED", p.getCancellationStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getRefundAmount()));
        verify(events).bookingCancelled(p);
    }

    @Test
    void paidOnlineBookingIsRefundedInFullByDefault() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "onlinePaymentsEnabled", true);
        Parcel p = parcel("BOOKED", "ONLINE");
        var eligibility = service.eligibility(p.getId(), jwt(p.getCustomerId()));
        assertEquals(0, new BigDecimal("550.00").compareTo(eligibility.refundableAmount()));

        service.initiate(p.getId(), jwt(p.getCustomerId()));
        assertEquals("REFUND_PENDING", p.getCancellationStatus());
        assertEquals(0, new BigDecimal("550.00").compareTo(p.getRefundAmount()));
    }

    @Test
    void configuredFeeIsDeductedFromTheRefund() {
        var withFee = new BookingCancellationService(repo, events, new BigDecimal("100.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(withFee, "onlinePaymentsEnabled", true);
        Parcel p = parcel("BOOKED", "ONLINE");
        withFee.initiate(p.getId(), jwt(p.getCustomerId()));
        assertEquals(0, new BigDecimal("450.00").compareTo(p.getRefundAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(p.getCancellationFee()));
    }

    @Test
    void scheduledParcelCannotBeCancelled() {
        for (String status : new String[]{"ASSIGNED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            Parcel p = parcel(status, "COD");
            assertFalse(service.eligibility(p.getId(), jwt(p.getCustomerId())).eligible(), status);
            ApiException ex = assertThrows(ApiException.class, () -> service.initiate(p.getId(), jwt(p.getCustomerId())));
            assertEquals("CANCELLATION_NOT_ALLOWED", ex.getCode());
            assertEquals(status, p.getStatus());
        }
        verify(events, never()).bookingCancelled(any());
    }

    @Test
    void cancellingTwiceReturnsTheSameResult() {
        Parcel p = parcel("BOOKED", "COD");
        service.initiate(p.getId(), jwt(p.getCustomerId()));
        CancellationResultDto again = service.initiate(p.getId(), jwt(p.getCustomerId()));
        assertTrue(again.completed());
        verify(events, times(1)).bookingCancelled(any());
    }

    @Test
    void cancelledCannotBeReactivated() {
        Parcel p = new Parcel();
        p.setStatus("CANCELLED");
        assertThrows(IllegalStateException.class, () -> p.setStatus("BOOKED"));
    }

    @Test
    void onlineCancellationDoesNotPromiseARefundWhileDisabled() {
        Parcel p = parcel("BOOKED", "ONLINE");
        assertFalse(service.eligibility(p.getId(), jwt(p.getCustomerId())).eligible());
        assertThrows(ApiException.class, () -> service.initiate(p.getId(), jwt(p.getCustomerId())));
        assertEquals("BOOKED", p.getStatus());
        verifyNoInteractions(events);
    }
}
