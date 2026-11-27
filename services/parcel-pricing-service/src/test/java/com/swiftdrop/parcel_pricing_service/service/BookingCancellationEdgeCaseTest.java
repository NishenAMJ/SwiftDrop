package com.swiftdrop.parcel_pricing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Invalid cancellations, fee capping and the read-only cancellation endpoints. */
class BookingCancellationEdgeCaseTest {
    private final ParcelRepository repo = mock(ParcelRepository.class);
    private final ParcelEventPublisher events = mock(ParcelEventPublisher.class);

    private BookingCancellationService service(String fee) {
        return new BookingCancellationService(repo, events, new BigDecimal(fee));
    }

    private BookingCancellationService onlineService(String fee) {
        BookingCancellationService service = service(fee);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "onlinePaymentsEnabled", true);
        return service;
    }

    private static Jwt jwt(UUID id) {
        return Jwt.withTokenValue("test").header("alg", "RS256").subject(id.toString()).build();
    }

    private Parcel parcel(String status, String method, String paymentStatus) {
        Parcel p = new Parcel();
        p.setCustomerId(UUID.randomUUID());
        p.setStatus(status);
        p.setPaymentMethod(method);
        p.setPaymentStatus(paymentStatus);
        p.setEstimatedFee(new BigDecimal("550.00"));
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        when(repo.lockById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHECKOUT_DRAFT", "EXPIRED", "ABANDONED"})
    void unpaidDraftsCannotBeCancelled(String status) {
        Parcel p = parcel(status, "ONLINE", "UNPAID");
        var eligibility = service("0").eligibility(p.getId(), jwt(p.getCustomerId()));
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("never confirmed");
        assertThatThrownBy(() -> service("0").initiate(p.getId(), jwt(p.getCustomerId())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CANCELLATION_NOT_ALLOWED"));
        verifyNoInteractions(events);
    }

    @Test
    void unpaidOnlineBookingThatSlippedPastDraftIsNotCancellable() {
        Parcel p = parcel("BOOKED", "ONLINE", "UNPAID");
        assertThat(service("0").eligibility(p.getId(), jwt(p.getCustomerId())).eligible()).isFalse();
    }

    @Test
    void deliveredParcelCannotBeCancelled() {
        Parcel p = parcel("DELIVERED", "COD", "PENDING");
        assertThat(service("0").eligibility(p.getId(), jwt(p.getCustomerId())).reason()).contains("already been delivered");
        assertThatThrownBy(() -> service("0").initiate(p.getId(), jwt(p.getCustomerId()))).isInstanceOf(ApiException.class);
    }

    @Test
    void cancellationFeeIsCappedAtWhatWasPaid() {
        Parcel p = parcel("BOOKED", "ONLINE", "PAID");
        var eligibility = onlineService("900.00").eligibility(p.getId(), jwt(p.getCustomerId()));
        assertThat(eligibility.cancellationFee()).isEqualByComparingTo("550.00");
        assertThat(eligibility.refundableAmount()).isEqualByComparingTo("0");

        var result = onlineService("900.00").initiate(p.getId(), jwt(p.getCustomerId()));
        assertThat(result.refundAmount()).isEqualByComparingTo("0");
        assertThat(result.cancellationStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void negativeConfiguredFeeIsTreatedAsZero() {
        Parcel p = parcel("BOOKED", "ONLINE", "PAID");
        var result = onlineService("-50").initiate(p.getId(), jwt(p.getCustomerId()));
        assertThat(result.cancellationFee()).isEqualByComparingTo("0");
        assertThat(result.refundAmount()).isEqualByComparingTo("550.00");
        assertThat(result.cancellationStatus()).isEqualTo("REFUND_PENDING");
    }

    @Test
    void codBookingIsNeverChargedAFeeAndNeedsNoPayment() {
        Parcel p = parcel("BOOKED", "COD", "PENDING");
        var eligibility = service("100").eligibility(p.getId(), jwt(p.getCustomerId()));
        assertThat(eligibility.cancellationFee()).isEqualByComparingTo("0");
        assertThat(eligibility.refundableAmount()).isNull();

        service("100").initiate(p.getId(), jwt(p.getCustomerId()));
        assertThat(p.getPaymentStatus()).isEqualTo("NOT_REQUIRED");
        verify(events).bookingCancelled(p);
    }

    @Test
    void initiateRejectsUnknownBookingsAndOtherCustomers() {
        UUID missing = UUID.randomUUID();
        when(repo.lockById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service("0").initiate(missing, jwt(UUID.randomUUID())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        Parcel p = parcel("BOOKED", "COD", "PENDING");
        assertThatThrownBy(() -> service("0").initiate(p.getId(), jwt(UUID.randomUUID())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(p.getStatus()).isEqualTo("BOOKED");
        verify(events, org.mockito.Mockito.never()).bookingCancelled(any());
    }

    @Test
    void statusIsOwnerOnlyAndReceiptRequiresACancelledBooking() {
        Parcel p = parcel("BOOKED", "COD", "PENDING");
        assertThat(service("0").status(p.getId(), jwt(p.getCustomerId())).bookingStatus()).isEqualTo("BOOKED");
        assertThatThrownBy(() -> service("0").status(p.getId(), jwt(UUID.randomUUID()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service("0").receipt(p.getId(), jwt(p.getCustomerId())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_CANCELLED"));

        service("0").initiate(p.getId(), jwt(p.getCustomerId()));
        assertThat(service("0").receipt(p.getId(), jwt(p.getCustomerId())).completed()).isTrue();
    }
}
