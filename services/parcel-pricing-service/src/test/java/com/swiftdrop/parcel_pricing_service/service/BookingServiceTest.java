package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.entity.*;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private BookingReceiptRepository receiptRepository;
    @Mock private ReferenceGenerator referenceGenerator;
    @Mock private PricingService pricingService;
    @Mock private ZoneRepository zoneRepository;
    @Mock private IdentityClient identityClient;
    @Mock private ParcelEventPublisher events;

    private BookingService bookingService;
    private final UUID customerId = UUID.randomUUID();

    @Test
    void customerCodReceiptMarksPaidWithoutRegressingDeliveryOrAcceptingOtherAmounts() {
        Parcel parcel = new Parcel();
        UUID id = UUID.randomUUID();
        parcel.setId(id); parcel.setCustomerId(customerId); parcel.setChannel("DIRECT_WEBSITE");
        parcel.setPaymentMethod("COD"); parcel.setStatus(BookingStatus.OUT_FOR_DELIVERY);
        parcel.setPaymentStatus("UNPAID"); parcel.setEstimatedFee(new BigDecimal("450.00"));
        parcel.setPricingSnapshotId("SNP-CUSTOMER");
        when(parcelRepository.lockById(id)).thenReturn(Optional.of(parcel));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
            bookingService.confirmCustomerCod(id.toString(), customerId,"SNP-CUSTOMER",new BigDecimal("400.00"),"LKR","2026-11-22T00:00:00Z"));
        assertEquals("UNPAID",parcel.getPaymentStatus());
        bookingService.confirmCustomerCod(id.toString(), customerId,"SNP-CUSTOMER",new BigDecimal("450.00"),"LKR","2026-11-22T00:00:00Z");
        assertEquals("PAID",parcel.getPaymentStatus());
        assertEquals(BookingStatus.OUT_FOR_DELIVERY,parcel.getStatus());
        var paidAt = parcel.getPaidAt();
        bookingService.confirmCustomerCod(id.toString(), customerId,"SNP-CUSTOMER",new BigDecimal("450.00"),"LKR","2026-11-22T00:01:00Z");
        assertEquals(paidAt, parcel.getPaidAt());
        parcel.setStatus(BookingStatus.CANCELLED);
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
            bookingService.confirmCustomerCod(id.toString(), customerId,"SNP-CUSTOMER",new BigDecimal("450.00"),"LKR","2026-11-22T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(parcelRepository, receiptRepository, referenceGenerator,
                pricingService, zoneRepository, identityClient, events);
        org.springframework.test.util.ReflectionTestUtils.setField(bookingService, "onlinePaymentsEnabled", true);
        when(referenceGenerator.generateTrackingNumber()).thenReturn("SWD-2026-000001");
        when(referenceGenerator.generateReceiptNumber("SWD-2026-000001")).thenReturn("RCP-2026-000001");
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(i -> i.getArgument(0));
        when(receiptRepository.save(any(BookingReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(receiptRepository.findByParcelId(any())).thenReturn(Optional.empty());
        when(identityClient.currentUser(any())).thenReturn(Optional.of(
                new IdentityUser(customerId, "nimal@example.com", "Nimal Perera", "+94771234567")));
        when(pricingService.calculateFee(any())).thenReturn(quote(false));
    }

    private Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject.toString())
                .claim("roles", List.of("CUSTOMER")).build();
    }

    /** What PricingService returns for 2 kg to COLOMBO with the seeded STANDARD_DEFAULT rule. */
    private static PricingResponse quote(boolean urgent) {
        BigDecimal surcharge = urgent ? new BigDecimal("200.00") : new BigDecimal("0.00");
        return new PricingResponse(new BigDecimal("550.00").add(surcharge), new BigDecimal("300.00"), new BigDecimal("100.00"),
                new BigDecimal("2.00"), new BigDecimal("50.00"), new BigDecimal("150.00"), "COLOMBO",
                surcharge, urgent, "LKR", "STANDARD_DEFAULT", "breakdown");
    }

    private static CreateBookingRequest request(String urgency, String method, String deliveryZone) {
        AddressDto pickup = new AddressDto("Nimal Sender", "0771234567", "12 Main St", "Colombo", "00100", null);
        AddressDto delivery = new AddressDto("Kamala Receiver", "0777654321", "45 Lake Rd", "Colombo", "00500", deliveryZone);
        return new CreateBookingRequest("Documents", new BigDecimal("2.00"), urgency, method, pickup, delivery);
    }

    // ------------------------------------------------------------ pricing

    @Test
    void feeComesFromThePricingModuleWithTheDeliveryZone() {
        BookingResponseDto response = bookingService.createBooking(request("NORMAL", "COD", "colombo"), jwt(customerId));

        ArgumentCaptor<PricingRequest> asked = ArgumentCaptor.forClass(PricingRequest.class);
        verify(pricingService).calculateFee(asked.capture());
        assertEquals("COLOMBO", asked.getValue().zoneCode());
        assertEquals(Boolean.FALSE, asked.getValue().isUrgent());
        assertEquals(new BigDecimal("550.00"), response.estimatedFee());
        assertEquals(new BigDecimal("150.00"), response.feeBreakdown().zoneCharge());
        assertEquals("STANDARD_DEFAULT", response.feeBreakdown().pricingRuleCode());
    }

    @Test
    void urgentBookingAsksPricingForTheUrgentSurcharge() {
        when(pricingService.calculateFee(any())).thenReturn(quote(true));
        BookingResponseDto response = bookingService.createBooking(request("URGENT", "ONLINE", "COLOMBO"), jwt(customerId));

        ArgumentCaptor<PricingRequest> asked = ArgumentCaptor.forClass(PricingRequest.class);
        verify(pricingService).calculateFee(asked.capture());
        assertEquals(Boolean.TRUE, asked.getValue().isUrgent());
        assertEquals("URGENT", response.urgency());
        assertEquals(new BigDecimal("750.00"), response.estimatedFee());
    }

    @Test
    void deliveryZoneIsRequired() {
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.createBooking(request("NORMAL", "COD", null), jwt(customerId)));
        assertEquals("ZONE_REQUIRED", ex.getCode());
        verifyNoInteractions(pricingService);
    }

    @Test
    void unknownUrgencyIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.createBooking(request("FAST", "COD", "COLOMBO"), jwt(customerId)));
        assertEquals("INVALID_URGENCY", ex.getCode());
    }

    @Test
    void unknownPickupZoneIsRejected() {
        AddressDto pickup = new AddressDto("Nimal", "0771234567", "12 Main St", "Colombo", null, "MARS");
        AddressDto delivery = new AddressDto("Kamala", "0777654321", "45 Lake Rd", "Colombo", null, "COLOMBO");
        when(zoneRepository.findByCodeIgnoreCase("MARS")).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> bookingService.createBooking(
                new CreateBookingRequest("x", BigDecimal.ONE, "NORMAL", "COD", pickup, delivery), jwt(customerId)));
        assertEquals("INVALID_ZONE", ex.getCode());
    }

    // ------------------------------------------------------------ COD and ONLINE creation

    @Test
    void codBookingIsConfirmedAtOnceWithReceiptAndEvents() {
        BookingResponseDto response = bookingService.createBooking(request("NORMAL", "COD", "COLOMBO"), jwt(customerId));

        assertEquals("BOOKED", response.status());
        assertEquals("PENDING", response.paymentStatus());
        assertEquals("RCP-2026-000001", response.receiptNumber());
        verify(events).bookingConfirmed(any(Parcel.class));
    }

    @Test
    void customerDetailsComeFromIdentityNotPlaceholders() {
        ArgumentCaptor<Parcel> saved = ArgumentCaptor.forClass(Parcel.class);
        bookingService.createBooking(request("NORMAL", "COD", "COLOMBO"), jwt(customerId));
        verify(parcelRepository).save(saved.capture());
        assertEquals("Nimal Perera", saved.getValue().getCustomerName());
        assertEquals("nimal@example.com", saved.getValue().getCustomerEmail());
    }

    @Test
    void onlineBookingStaysAnUnpaidDraftWithoutReceiptOrEvents() {
        BookingResponseDto response = bookingService.createBooking(request("NORMAL", "ONLINE", "COLOMBO"), jwt(customerId));

        assertEquals("CHECKOUT_DRAFT", response.status());
        assertEquals("UNPAID", response.paymentStatus());
        assertNull(response.receiptNumber());
        assertNotNull(response.pricingExpiresAt());
        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    // ------------------------------------------------------------ payment confirmation

    private Parcel unpaidDraft() {
        Parcel p = new Parcel();
        p.setCustomerId(customerId);
        p.setStatus("CHECKOUT_DRAFT");
        p.setPaymentMethod("ONLINE");
        p.setPaymentStatus("UNPAID");
        p.setEstimatedFee(new BigDecimal("550.00"));
        p.setTrackingNumber("SWD-2026-000001");
        when(parcelRepository.lockById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private static VerifiedPayment payment(Parcel p, String status) {
        return new VerifiedPayment(p.getId().toString(), p.getEstimatedFee(), "LKR", status, "PAYHERE-123");
    }

    @Test
    void successfulMatchingPaymentConfirmsOnceAndIssuesOneReceipt() {
        Parcel p = unpaidDraft();
        UUID paymentId = UUID.randomUUID();

        BookingResponseDto result = bookingService.confirmPaid(p.getId(), customerId, paymentId, payment(p, "SUCCESS"));
        assertEquals("BOOKED", result.status());
        assertEquals("PAID", p.getPaymentStatus());
        assertEquals(paymentId, p.getPaymentId());
        assertNotNull(result.receiptNumber());
        verify(events).bookingConfirmed(p);

        // A repeat (browser retry or the payment.succeeded event) changes nothing.
        when(receiptRepository.findByParcelId(p.getId())).thenReturn(Optional.of(new BookingReceipt("RCP-2026-000001", p, p.getEstimatedFee(), "LKR")));
        bookingService.confirmPaid(p.getId(), customerId, paymentId, payment(p, "SUCCESS"));
        bookingService.confirmPaidFromEvent(p.getId().toString(), paymentId, p.getEstimatedFee(), "LKR");
        verify(receiptRepository, times(1)).save(any());
        verify(events, times(1)).bookingConfirmed(any());
    }

    @Test
    void paymentEventConfirmsBookingWithoutTheBrowser() {
        Parcel p = unpaidDraft();
        bookingService.confirmPaidFromEvent(p.getId().toString(), UUID.randomUUID(), new BigDecimal("550.00"), "LKR");
        assertEquals("BOOKED", p.getStatus());
        verify(events).bookingConfirmed(p);
    }

    @Test
    void latePaymentStillConfirmsAnExpiredCheckout() {
        Parcel p = unpaidDraft();
        p.setStatus("EXPIRED");
        bookingService.confirmPaidFromEvent(p.getId().toString(), UUID.randomUUID(), new BigDecimal("550.00"), "LKR");
        assertEquals("BOOKED", p.getStatus());
    }

    @Test
    void pendingFailedOrMismatchedPaymentsCannotConfirm() {
        Parcel p = unpaidDraft();
        assertThrows(ApiException.class, () -> bookingService.confirmPaid(p.getId(), customerId, UUID.randomUUID(), payment(p, "PENDING")));
        assertThrows(ApiException.class, () -> bookingService.confirmPaid(p.getId(), customerId, UUID.randomUUID(), payment(p, "FAILED")));
        VerifiedPayment otherBooking = new VerifiedPayment(UUID.randomUUID().toString(), p.getEstimatedFee(), "LKR", "SUCCESS", "X");
        assertThrows(ApiException.class, () -> bookingService.confirmPaid(p.getId(), customerId, UUID.randomUUID(), otherBooking));
        VerifiedPayment wrongAmount = new VerifiedPayment(p.getId().toString(), BigDecimal.ONE, "LKR", "SUCCESS", "X");
        assertThrows(ApiException.class, () -> bookingService.confirmPaid(p.getId(), customerId, UUID.randomUUID(), wrongAmount));
        assertEquals("CHECKOUT_DRAFT", p.getStatus());
        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void anotherCustomerCannotConfirm() {
        Parcel p = unpaidDraft();
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.confirmPaid(p.getId(), UUID.randomUUID(), UUID.randomUUID(), payment(p, "SUCCESS")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void failedPaymentEventKeepsTheDraftForRetry() {
        Parcel p = unpaidDraft();
        bookingService.markPaymentFailed(p.getId().toString());
        assertEquals("CHECKOUT_DRAFT", p.getStatus());
        assertEquals("FAILED", p.getPaymentStatus());
    }

    @Test
    void customerCanDiscardOnlyAnUnpaidDraft() {
        Parcel p = unpaidDraft();
        bookingService.discardDraft(p.getId(), customerId);
        assertEquals("ABANDONED", p.getStatus());
        assertThrows(ApiException.class, () -> bookingService.discardDraft(p.getId(), customerId));
    }

    // ------------------------------------------------------------ reads

    @Test
    void historyHidesUnpaidDraftsAndShowsConfirmedBookings() {
        Parcel draft = new Parcel(); draft.setCustomerId(customerId); draft.setStatus("CHECKOUT_DRAFT");
        Parcel expired = new Parcel(); expired.setCustomerId(customerId); expired.setStatus("EXPIRED");
        Parcel unpaidLegacy = new Parcel(); unpaidLegacy.setCustomerId(customerId); unpaidLegacy.setStatus("BOOKED");
        Parcel paid = new Parcel(); paid.setCustomerId(customerId); paid.setStatus("IN_TRANSIT"); paid.setPaymentStatus("PAID");
        Parcel cod = new Parcel(); cod.setCustomerId(customerId); cod.setStatus("BOOKED"); cod.setPaymentMethod("COD");
        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(draft, expired, unpaidLegacy, paid, cod));

        List<BookingSummaryDto> history = bookingService.getCustomerBookings(customerId);
        assertEquals(2, history.size());
        assertFalse(history.get(0).cancellable());
        assertTrue(history.get(1).cancellable());
    }

    @Test
    void trackingByReferenceOnlyFindsYourOwnBooking() {
        Parcel p = new Parcel(); p.setCustomerId(UUID.randomUUID()); p.setStatus("BOOKED"); p.setPaymentMethod("COD");
        p.setTrackingNumber("SWD-2026-000009");
        when(parcelRepository.findByTrackingNumber("SWD-2026-000009")).thenReturn(Optional.of(p));
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.getOwnedBookingByTrackingNumber("swd-2026-000009", customerId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void draftHasNoReceipt() {
        Parcel p = unpaidDraft();
        when(parcelRepository.findById(p.getId())).thenReturn(Optional.of(p));
        ApiException ex = assertThrows(ApiException.class, () -> bookingService.getReceipt(p.getId(), customerId));
        assertEquals("PAYMENT_REQUIRED", ex.getCode());
    }

    // ------------------------------------------------------------ payment context

    @Test
    void paymentContextUsesTheRealCustomerAndPickupAddress() {
        Parcel p = unpaidDraft();
        p.setCustomerName("Nimal Perera");
        p.setCustomerEmail("nimal@example.com");
        p.setCustomerPhone("+94771234567");
        p.setPricingExpiresAt(OffsetDateTime.now().plusHours(1));
        p.addAddress(new ParcelAddress("PICKUP", "Nimal", "0771234567", "12 Main St", "Colombo", null, null));
        when(parcelRepository.findById(p.getId())).thenReturn(Optional.of(p));

        PaymentContextResponse context = bookingService.getPaymentContext(p.getId(), jwt(customerId));
        assertTrue(context.payable());
        assertEquals("Nimal", context.payer().firstName());
        assertEquals("Perera", context.payer().lastName());
        assertEquals("nimal@example.com", context.payer().email());
        assertEquals("12 Main St", context.payer().address());
    }

    @Test
    void paymentContextNeverInventsAnEmail() {
        Parcel p = unpaidDraft();
        p.addAddress(new ParcelAddress("PICKUP", "Nimal", "0771234567", "12 Main St", "Colombo", null, null));
        when(parcelRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(identityClient.currentUser(any())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.getPaymentContext(p.getId(), jwt(customerId)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("PAYER_DETAILS_MISSING", ex.getCode());
    }

    @Test
    void paymentContextErrorsKeepTheirStatusForPaymentService() {
        UUID missing = UUID.randomUUID();
        when(parcelRepository.findById(missing)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> bookingService.getPaymentContext(missing, jwt(customerId))).getStatus());

        Parcel p = unpaidDraft();
        when(parcelRepository.findById(p.getId())).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> bookingService.getPaymentContext(p.getId(), jwt(UUID.randomUUID()))).getStatus());
    }

    @Test void cancellationRequiresMatchingPaymentAndFullRefundTarget() {
        UUID id=UUID.randomUUID(), payment=UUID.randomUUID();
        Parcel parcel=new Parcel(); parcel.setPaymentId(payment); parcel.setStatus(BookingStatus.CANCELLED);
        parcel.setPaymentStatus("SUCCESS"); parcel.setCancellationStatus("REFUND_PENDING");
        parcel.setRefundAmount(new BigDecimal("900.00"));
        when(parcelRepository.lockById(id)).thenReturn(Optional.of(parcel));
        bookingService.markRefunded(id.toString(),"REFUNDED",UUID.randomUUID(),new BigDecimal("1000"));
        assertEquals("REFUND_PENDING",parcel.getCancellationStatus());
        bookingService.markRefunded(id.toString(),"PARTIALLY_REFUNDED",payment,new BigDecimal("200"));
        assertEquals("REFUND_PENDING",parcel.getCancellationStatus());
        bookingService.markRefunded(id.toString(),"PARTIALLY_REFUNDED",payment,new BigDecimal("900"));
        assertEquals("REFUNDED",parcel.getCancellationStatus());
    }
}
