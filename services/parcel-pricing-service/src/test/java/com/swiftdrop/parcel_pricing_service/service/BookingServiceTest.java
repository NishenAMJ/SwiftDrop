package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.entity.*;
import com.swiftdrop.parcel_pricing_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private BookingReceiptRepository receiptRepository;

    @Mock
    private ReferenceGenerator referenceGenerator;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(parcelRepository, receiptRepository, referenceGenerator);
    }

    @Test
    void createBooking_normalUrgency_calculatesCorrectFee() {
        UUID customerId = UUID.randomUUID();
        when(referenceGenerator.generateTrackingNumber()).thenReturn("SWD-2026-000001");
        when(referenceGenerator.generateReceiptNumber("SWD-2026-000001")).thenReturn("RCP-2026-000001");

        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.save(any(BookingReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressDto pickup = new AddressDto("John Sender", "0771234567", "12 Main St", "Colombo", "00100", "Z-COL-1");
        AddressDto delivery = new AddressDto("Jane Recipient", "0777654321", "45 Lake Rd", "Kandy", "20000", "Z-KAN-1");
        CreateBookingRequest request = new CreateBookingRequest(
            "Electronics Package",
            new BigDecimal("2.00"), // 2 kg
            "NORMAL",
            "COD",
            pickup,
            delivery
        );

        BookingResponseDto response = bookingService.createBooking(request, customerId);

        assertNotNull(response);
        assertEquals("SWD-2026-000001", response.trackingNumber());
        assertEquals("NORMAL", response.urgency());
        assertEquals("BOOKED", response.status());
        // Fee = 350 base + (2.00 * 150) = 650
        assertEquals(new BigDecimal("650.00"), response.estimatedFee());
        assertEquals("RCP-2026-000001", response.receiptNumber());

        verify(parcelRepository, times(1)).save(any(Parcel.class));
        verify(receiptRepository, times(1)).save(any(BookingReceipt.class));
    }

    @Test
    void createBooking_urgentPriority_addsUrgentSurcharge() {
        UUID customerId = UUID.randomUUID();
        when(referenceGenerator.generateTrackingNumber()).thenReturn("SWD-2026-000002");


        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));


        AddressDto pickup = new AddressDto("John", "0771234567", "12 Main St", "Colombo", "00100", "Z-COL-1");
        AddressDto delivery = new AddressDto("Jane", "0777654321", "45 Lake Rd", "Kandy", "20000", "Z-KAN-1");
        CreateBookingRequest request = new CreateBookingRequest(
            "Urgent Medical Supplies",
            new BigDecimal("1.00"), // 1 kg
            "URGENT",
            pickup,
            delivery
        );

        BookingResponseDto response = bookingService.createBooking(request, customerId);

        assertNotNull(response);
        assertEquals("URGENT", response.urgency());
        assertEquals("CHECKOUT_DRAFT",response.status());
        assertNull(response.receiptNumber());
        verifyNoInteractions(receiptRepository);
        // Fee = 350 base + (1.00 * 150) + 300 urgent surcharge = 800
        assertEquals(new BigDecimal("800.00"), response.estimatedFee());
    }

    @Test
    void getCustomerBookings_returnsBookingSummaries() {
        UUID customerId = UUID.randomUUID();
        Parcel p = new Parcel();
        p.setTrackingNumber("SWD-2026-000003");
        p.setCustomerId(customerId);
        p.setUrgency("NORMAL");
        p.setStatus("BOOKED");
        p.setPaymentStatus("PAID");
        p.setWeightKg(new BigDecimal("3.50"));
        p.setEstimatedFee(new BigDecimal("875.00"));

        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(p));

        List<BookingSummaryDto> result = bookingService.getCustomerBookings(customerId);

        assertEquals(1, result.size());
        assertEquals("SWD-2026-000003", result.get(0).trackingNumber());
    }

    private Parcel unpaidDraft() {
        Parcel p=new Parcel(); p.setCustomerId(UUID.randomUUID()); p.setStatus("CHECKOUT_DRAFT");
        p.setPaymentMethod("ONLINE"); p.setEstimatedFee(new BigDecimal("500.00"));
        p.setTrackingNumber("SWD-TEST");
        when(parcelRepository.lockById(p.getId())).thenReturn(Optional.of(p)); return p;
    }
    private com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment payment(Parcel p,String status) {
        return new com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment(p.getId().toString(),p.getEstimatedFee(),"LKR",status,"123456");
    }
    @Test void pendingPaymentCannotConfirmBookingOrIssueReceipt() {
        var p=unpaidDraft();
        assertThrows(RuntimeException.class,()->bookingService.confirmPaid(p.getId(),p.getCustomerId(),payment(p,"PENDING")));
        assertEquals("CHECKOUT_DRAFT",p.getStatus()); verifyNoInteractions(receiptRepository);
    }
    @Test void successfulMatchingPaymentConfirmsAndRetriesWithoutSecondReceipt() {
        var p=unpaidDraft();
        when(receiptRepository.findByParcelId(p.getId())).thenReturn(Optional.empty());
        when(referenceGenerator.generateReceiptNumber("SWD-TEST")).thenReturn("RCP-TEST");
        when(receiptRepository.save(any())).thenAnswer(i->i.getArgument(0));
        var result=bookingService.confirmPaid(p.getId(),p.getCustomerId(),payment(p,"SUCCESS"));
        assertEquals("BOOKED",result.status()); assertEquals("PAID",p.getPaymentStatus()); assertNotNull(result.receiptNumber());
        when(receiptRepository.findByParcelId(p.getId())).thenReturn(Optional.of(new BookingReceipt("RCP-TEST",p,p.getEstimatedFee(),"LKR")));
        bookingService.confirmPaid(p.getId(),p.getCustomerId(),payment(p,"SUCCESS"));
        verify(receiptRepository,times(1)).save(any());
    }
    @Test void mismatchedPaymentCannotConfirm() {
        var p=unpaidDraft();
        var wrong=new com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment(UUID.randomUUID().toString(),p.getEstimatedFee(),"LKR","SUCCESS","123");
        assertThrows(RuntimeException.class,()->bookingService.confirmPaid(p.getId(),p.getCustomerId(),wrong));
        verifyNoInteractions(receiptRepository);
    }
    @Test void draftHasNoReceiptEvenWhenAccessedDirectly() {
        var p=new Parcel(); when(parcelRepository.findById(p.getId())).thenReturn(Optional.of(p));
        assertThrows(RuntimeException.class,()->bookingService.getReceiptByParcelId(p.getId()));
        verifyNoInteractions(receiptRepository);
    }

    @Test void failedPaymentCannotConfirm() {
        var p=unpaidDraft();
        assertThrows(RuntimeException.class,()->bookingService.confirmPaid(p.getId(),p.getCustomerId(),payment(p,"FAILED")));
        assertEquals("CHECKOUT_DRAFT",p.getStatus()); verifyNoInteractions(receiptRepository);
    }
    @Test void incorrectAmountCannotConfirm() {
        var p=unpaidDraft();
        var wrong=new com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment(p.getId().toString(),BigDecimal.ONE,"LKR","SUCCESS","123");
        assertThrows(RuntimeException.class,()->bookingService.confirmPaid(p.getId(),p.getCustomerId(),wrong));
        verifyNoInteractions(receiptRepository);
    }
    @Test void anotherCustomerCannotConfirm() {
        var p=unpaidDraft();
        assertThrows(RuntimeException.class,()->bookingService.confirmPaid(p.getId(),UUID.randomUUID(),payment(p,"SUCCESS")));
        verifyNoInteractions(receiptRepository);
    }
    @Test void unpaidOnlineAndCheckoutDraftsAreExcludedFromHistory() {
        var customer=UUID.randomUUID();
        var draft=new Parcel(); draft.setCustomerId(customer); draft.setStatus("CHECKOUT_DRAFT");
        var legacyUnpaid=new Parcel(); legacyUnpaid.setCustomerId(customer); legacyUnpaid.setStatus("BOOKED");
        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customer)).thenReturn(List.of(draft,legacyUnpaid));
        assertTrue(bookingService.getCustomerBookings(customer).isEmpty());
    }
}

