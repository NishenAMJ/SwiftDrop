package com.swiftdrop.parcel_pricing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.AddressDto;
import com.swiftdrop.parcel_pricing_service.dto.CreateBookingRequest;
import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryZone;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.entity.ZoneType;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.BookingReceiptRepository;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import com.swiftdrop.parcel_pricing_service.repository.ZoneRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Invalid inputs, duplicate/late payment events, ownership and payment-context edge cases of bookings. */
class BookingServiceEdgeCaseTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final BookingReceiptRepository receipts = mock(BookingReceiptRepository.class);
    private final ReferenceGenerator references = mock(ReferenceGenerator.class);
    private final PricingService pricing = mock(PricingService.class);
    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final ParcelEventPublisher events = mock(ParcelEventPublisher.class);
    private BookingService service;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BookingService(parcels, receipts, references, pricing, zones, identity, events);
        when(receipts.findByParcelId(any())).thenReturn(Optional.empty());
        when(receipts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(references.generateReceiptNumber(any())).thenReturn("RCP-2026-000001");
    }

    private Jwt jwt(UUID subject, String... roles) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject.toString())
                .claim("roles", List.of(roles.length == 0 ? new String[]{"CUSTOMER"} : roles)).build();
    }

    private Parcel parcel(String status, String method) {
        Parcel p = new Parcel();
        p.setCustomerId(customerId);
        p.setStatus(status);
        p.setPaymentMethod(method);
        p.setPaymentStatus("UNPAID");
        p.setChannel("DIRECT_WEBSITE");
        p.setEstimatedFee(new BigDecimal("550.00"));
        p.setTrackingNumber("SWD-2026-000001");
        p.setPricingSnapshotId("SNP-ABC");
        when(parcels.lockById(p.getId())).thenReturn(Optional.of(p));
        when(parcels.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private static VerifiedPayment success(Parcel p, String amount, String currency, String reference) {
        return new VerifiedPayment(p.getId().toString(), new BigDecimal(amount), currency, "SUCCESS", reference);
    }

    private static void assertApi(HttpStatus status, String code, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            if (code != null) assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---------- create ----------

    private static CreateBookingRequest request(String method, String pickupZone) {
        AddressDto pickup = new AddressDto("Nimal Sender", "0771234567", "12 Main St", "Colombo", "00100", pickupZone);
        AddressDto delivery = new AddressDto("Kamala Receiver", "0777654321", "45 Lake Rd", "Colombo", "00500", "COLOMBO");
        return new CreateBookingRequest("Documents", new BigDecimal("2.00"), "NORMAL", method, pickup, delivery);
    }

    @Test
    void unknownPaymentMethodIsRejected() {
        assertApi(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD",
                () -> service.createBooking(request("CARD", null), jwt(customerId)));
        verifyNoInteractions(pricing, parcels);
    }

    @Test
    void inactivePickupZoneIsRejected() {
        DeliveryZone zone = new DeliveryZone("Kandy", "KANDY", null, ZoneType.GENERAL);
        zone.setStatus(ZoneStatus.INACTIVE);
        when(zones.findByCodeIgnoreCase("KANDY")).thenReturn(Optional.of(zone));
        assertApi(HttpStatus.BAD_REQUEST, "INACTIVE_ZONE",
                () -> service.createBooking(request("COD", " kandy "), jwt(customerId)));
        verifyNoInteractions(pricing);
    }

    @Test
    void bookingWithoutAValidLoginIsUnauthorised() {
        Jwt notAUuid = Jwt.withTokenValue("t").header("alg", "RS256").subject("not-a-uuid").build();
        assertApi(HttpStatus.UNAUTHORIZED, null, () -> service.createBooking(request("COD", null), notAUuid));
        assertApi(HttpStatus.UNAUTHORIZED, null, () -> service.createBooking(request("COD", null), null));
    }

    // ---------- browser confirmation ----------

    @Test
    void paymentWithoutAProviderReferenceCannotConfirm() {
        Parcel p = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        assertApi(HttpStatus.CONFLICT, "PAYMENT_NOT_VERIFIED",
                () -> service.confirmPaid(p.getId(), customerId, UUID.randomUUID(), success(p, "550.00", "LKR", " ")));
        assertApi(HttpStatus.CONFLICT, "PAYMENT_NOT_VERIFIED",
                () -> service.confirmPaid(p.getId(), customerId, UUID.randomUUID(), success(p, "550.00", "LKR", null)));
    }

    @Test
    void paymentInAnotherCurrencyCannotConfirm() {
        Parcel p = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        assertApi(HttpStatus.CONFLICT, "PAYMENT_NOT_VERIFIED",
                () -> service.confirmPaid(p.getId(), customerId, UUID.randomUUID(), success(p, "550.00", "USD", "PH-1")));
        assertThat(p.getStatus()).isEqualTo(BookingStatus.CHECKOUT_DRAFT);
    }

    @Test
    void codBookingCannotBeConfirmedByAnOnlinePayment() {
        Parcel p = parcel(BookingStatus.BOOKED, "COD");
        assertApi(HttpStatus.CONFLICT, "PAYMENT_NOT_VERIFIED",
                () -> service.confirmPaid(p.getId(), customerId, UUID.randomUUID(), success(p, "550.00", "LKR", "PH-1")));
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Parcel p = parcel(BookingStatus.CANCELLED, "ONLINE");
        assertApi(HttpStatus.CONFLICT, "INVALID_BOOKING_STATE",
                () -> service.confirmPaid(p.getId(), customerId, UUID.randomUUID(), success(p, "550.00", "LKR", "PH-1")));
        verifyNoInteractions(events);
    }

    @Test
    void amountComparisonIgnoresTrailingZeros() {
        Parcel p = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        var result = service.confirmPaid(p.getId(), customerId, UUID.randomUUID(), success(p, "550", "LKR", "PH-1"));
        assertThat(result.status()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void confirmingAMissingBookingIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(parcels.lockById(missing)).thenReturn(Optional.empty());
        assertApi(HttpStatus.NOT_FOUND, null, () -> service.confirmPaid(missing, customerId, UUID.randomUUID(),
                new VerifiedPayment(missing.toString(), BigDecimal.TEN, "LKR", "SUCCESS", "PH-1")));
    }

    // ---------- payment.succeeded events ----------

    @Test
    void paymentEventForMalformedOrUnknownBookingIsIgnored() {
        assertThatCode(() -> service.confirmPaidFromEvent("not-a-uuid", UUID.randomUUID(), BigDecimal.TEN, "LKR"))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.confirmPaidFromEvent(null, UUID.randomUUID(), BigDecimal.TEN, "LKR"))
                .doesNotThrowAnyException();
        UUID unknown = UUID.randomUUID();
        when(parcels.lockById(unknown)).thenReturn(Optional.empty());
        assertThatCode(() -> service.confirmPaidFromEvent(unknown.toString(), UUID.randomUUID(), BigDecimal.TEN, "LKR"))
                .doesNotThrowAnyException();
        verifyNoInteractions(events, receipts);
    }

    @Test
    void mismatchedPaymentEventIsLoggedForAPersonNotRetriedOrApplied() {
        Parcel p = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        assertThatCode(() -> service.confirmPaidFromEvent(p.getId().toString(), UUID.randomUUID(), new BigDecimal("1.00"), "LKR"))
                .doesNotThrowAnyException();
        Parcel cancelled = parcel(BookingStatus.CANCELLED, "ONLINE");
        assertThatCode(() -> service.confirmPaidFromEvent(cancelled.getId().toString(), UUID.randomUUID(), new BigDecimal("550.00"), "LKR"))
                .doesNotThrowAnyException();
        assertThat(p.getStatus()).isEqualTo(BookingStatus.CHECKOUT_DRAFT);
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(events);
    }

    // ---------- customer COD receipts ----------

    @Test
    void customerCodReceiptMustMatchTheBookingExactly() {
        Parcel p = parcel(BookingStatus.OUT_FOR_DELIVERY, "COD");
        String id = p.getId().toString();
        BigDecimal fee = new BigDecimal("550.00");
        String at = "2026-11-22T00:00:00Z";

        assertThatThrownBy(() -> service.confirmCustomerCod(id, UUID.randomUUID(), "SNP-ABC", fee, "LKR", at))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.confirmCustomerCod(id, null, "SNP-ABC", fee, "LKR", at))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.confirmCustomerCod(id, customerId, "SNP-OTHER", fee, "LKR", at))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.confirmCustomerCod(id, customerId, "SNP-ABC", fee, "USD", at))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.confirmCustomerCod(id, customerId, "SNP-ABC", null, "LKR", at))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.confirmCustomerCod(id, customerId, "SNP-ABC", fee, "LKR", "yesterday"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.confirmCustomerCod("bad-id", customerId, "SNP-ABC", fee, "LKR", at))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(p.getPaymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void merchantOrOnlineBookingsDoNotAcceptCustomerCashReceipts() {
        Parcel merchantOrder = parcel(BookingStatus.OUT_FOR_DELIVERY, "COD");
        merchantOrder.setMerchantId(UUID.randomUUID());
        assertThatThrownBy(() -> service.confirmCustomerCod(merchantOrder.getId().toString(), customerId, "SNP-ABC",
                new BigDecimal("550.00"), "LKR", "2026-11-22T00:00:00Z")).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        Parcel online = parcel(BookingStatus.OUT_FOR_DELIVERY, "ONLINE");
        assertThatThrownBy(() -> service.confirmCustomerCod(online.getId().toString(), customerId, "SNP-ABC",
                new BigDecimal("550.00"), "LKR", "2026-11-22T00:00:00Z")).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    // ---------- payment.failed / payment.refunded ----------

    @Test
    void failedPaymentNeverOverwritesAPaidOrConfirmedBooking() {
        Parcel paidDraft = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        paidDraft.setPaymentStatus("PAID");
        service.markPaymentFailed(paidDraft.getId().toString());
        assertThat(paidDraft.getPaymentStatus()).isEqualTo("PAID");

        Parcel booked = parcel(BookingStatus.BOOKED, "ONLINE");
        booked.setPaymentStatus("PAID");
        service.markPaymentFailed(booked.getId().toString());
        assertThat(booked.getPaymentStatus()).isEqualTo("PAID");

        assertThatCode(() -> service.markPaymentFailed("garbage")).doesNotThrowAnyException();
    }

    @Test
    void refundEventFromAnotherPaymentOrWithInvalidDataIsIgnored() {
        Parcel p = parcel(BookingStatus.CANCELLED, "ONLINE");
        UUID paymentId = UUID.randomUUID();
        p.setPaymentId(paymentId);
        p.setPaymentStatus("PAID");
        p.setRefundAmount(new BigDecimal("450.00"));

        service.markRefunded(p.getId().toString(), "REFUNDED", UUID.randomUUID(), new BigDecimal("450.00"));
        service.markRefunded(p.getId().toString(), "REFUNDED", null, new BigDecimal("450.00"));
        service.markRefunded(p.getId().toString(), "REFUNDED", paymentId, new BigDecimal("-1"));
        service.markRefunded(p.getId().toString(), "REFUNDED", paymentId, null);
        service.markRefunded(p.getId().toString(), "CHARGEBACKED", paymentId, new BigDecimal("450.00"));
        service.markRefunded(null, "REFUNDED", paymentId, new BigDecimal("450.00"));

        assertThat(p.getPaymentStatus()).isEqualTo("PAID");
        assertThat(p.getCancellationStatus()).isNotEqualTo("REFUNDED");
    }

    @Test
    void partialRefundDoesNotCompleteCancellationAndFullRefundDoesNotRegress() {
        Parcel p = parcel(BookingStatus.CANCELLED, "ONLINE");
        UUID paymentId = UUID.randomUUID();
        p.setPaymentId(paymentId);
        p.setPaymentStatus("PAID");
        p.setRefundAmount(new BigDecimal("450.00"));
        p.setCancellationStatus("REFUND_PENDING");

        service.markRefunded(p.getId().toString(), "PARTIALLY_REFUNDED", paymentId, new BigDecimal("200.00"));
        assertThat(p.getPaymentStatus()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(p.getCancellationStatus()).isEqualTo("REFUND_PENDING");

        service.markRefunded(p.getId().toString(), "REFUNDED", paymentId, new BigDecimal("450.00"));
        assertThat(p.getCancellationStatus()).isEqualTo("REFUNDED");

        service.markRefunded(p.getId().toString(), "PARTIALLY_REFUNDED", paymentId, new BigDecimal("200.00"));
        assertThat(p.getPaymentStatus()).as("an older partial event must not regress a full refund").isEqualTo("REFUNDED");
    }

    // ---------- drafts ----------

    @Test
    void anotherCustomerCannotDiscardADraftButAnExpiredOneCanBe() {
        Parcel p = parcel(BookingStatus.EXPIRED, "ONLINE");
        assertApi(HttpStatus.FORBIDDEN, null, () -> service.discardDraft(p.getId(), UUID.randomUUID()));
        service.discardDraft(p.getId(), customerId);
        assertThat(p.getStatus()).isEqualTo(BookingStatus.ABANDONED);
    }

    @Test
    void expireStaleDraftsReportsHowManyWereExpired() {
        when(parcels.expireDrafts(any())).thenReturn(3);
        assertThat(service.expireStaleDrafts()).isEqualTo(3);
    }

    // ---------- reads ----------

    @Test
    void getBookingByIdReturnsOrNotFound() {
        Parcel p = parcel(BookingStatus.BOOKED, "COD");
        assertThat(service.getBookingById(p.getId()).trackingNumber()).isEqualTo("SWD-2026-000001");
        UUID missing = UUID.randomUUID();
        when(parcels.findById(missing)).thenReturn(Optional.empty());
        assertApi(HttpStatus.NOT_FOUND, null, () -> service.getBookingById(missing));
    }

    @Test
    void trackingNumberLookupIsNormalisedAndHidesDrafts() {
        Parcel p = parcel(BookingStatus.BOOKED, "COD");
        when(parcels.findByTrackingNumber("SWD-2026-000001")).thenReturn(Optional.of(p));
        assertThat(service.getOwnedBookingByTrackingNumber("  swd-2026-000001 ", customerId).id()).isEqualTo(p.getId());

        p.setStatus(BookingStatus.CHECKOUT_DRAFT);
        assertApi(HttpStatus.NOT_FOUND, null, () -> service.getOwnedBookingByTrackingNumber("SWD-2026-000001", customerId));
        when(parcels.findByTrackingNumber("")).thenReturn(Optional.empty());
        assertApi(HttpStatus.NOT_FOUND, null, () -> service.getOwnedBookingByTrackingNumber(null, customerId));
    }

    @Test
    void receiptRequiresAVisibleBookingAndAnIssuedReceipt() {
        Parcel draft = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        assertApi(HttpStatus.CONFLICT, "PAYMENT_REQUIRED", () -> service.getReceipt(draft.getId(), customerId));

        Parcel booked = parcel(BookingStatus.BOOKED, "COD");
        assertApi(HttpStatus.NOT_FOUND, null, () -> service.getReceipt(booked.getId(), customerId));
        assertApi(HttpStatus.FORBIDDEN, null, () -> service.getReceipt(booked.getId(), UUID.randomUUID()));

        when(receipts.findByParcelId(booked.getId())).thenReturn(Optional.of(
                new BookingReceipt("RCP-2026-000009", booked, booked.getEstimatedFee(), "LKR")));
        assertThat(service.getReceipt(booked.getId(), customerId).receiptNumber()).isEqualTo("RCP-2026-000009");
    }

    // ---------- payment context ----------

    private Parcel payable() {
        Parcel p = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        p.setPricingExpiresAt(OffsetDateTime.now().plusHours(1));
        p.addAddress(new ParcelAddress("PICKUP", "Nimal Sender", "0771234567", "12 Main St", "Colombo", null, null));
        return p;
    }

    @Test
    void paymentContextRequiresALogin() {
        assertApi(HttpStatus.UNAUTHORIZED, null, () -> service.getPaymentContext(UUID.randomUUID(), null));
    }

    @Test
    void dispatcherCanReadAnyContextButStrangersCannot() {
        Parcel p = payable();
        p.setCustomerName("Nimal Perera");
        p.setCustomerEmail("nimal@example.com");
        p.setCustomerPhone("0771234567");
        assertThat(service.getPaymentContext(p.getId(), jwt(UUID.randomUUID(), "DISPATCHER")).payable()).isTrue();
        assertApi(HttpStatus.FORBIDDEN, null, () -> service.getPaymentContext(p.getId(), jwt(UUID.randomUUID())));
        verify(identity, never()).currentUser(any());
    }

    @Test
    void merchantOwnerCanReadItsOrderContext() {
        Parcel p = payable();
        UUID merchant = UUID.randomUUID();
        p.setMerchantId(merchant);
        p.setChannel("MERCHANT_API");
        p.setCustomerName("Shop Owner");
        p.setCustomerEmail("shop@example.com");
        var context = service.getPaymentContext(p.getId(), jwt(merchant, "MERCHANT"));
        assertThat(context.channel()).isEqualTo("MERCHANT_API");
        assertThat(context.payer().phone()).as("falls back to pickup phone").isEqualTo("0771234567");
    }

    @Test
    void expiredOrAlreadyPaidCheckoutIsNotPayable() {
        Parcel expired = payable();
        expired.setCustomerEmail("nimal@example.com");
        expired.setPricingExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(identity.currentUser(any())).thenReturn(Optional.empty());
        assertThat(service.getPaymentContext(expired.getId(), jwt(customerId)).payable()).isFalse();

        Parcel paid = payable();
        paid.setCustomerEmail("nimal@example.com");
        paid.setPaymentStatus("PAID");
        assertThat(service.getPaymentContext(paid.getId(), jwt(customerId)).payable()).isFalse();
    }

    @Test
    void contextGeneratesASnapshotIdForLegacyBookingsAndSplitsSingleNames() {
        Parcel p = payable();
        p.setPricingSnapshotId(null);
        p.setChannel(null);
        when(identity.currentUser(any())).thenReturn(Optional.of(new IdentityUser(customerId, "madonna@example.com", "Madonna", null)));
        var context = service.getPaymentContext(p.getId(), jwt(customerId));
        assertThat(context.pricingSnapshotId()).startsWith("SNP-").hasSize(12);
        assertThat(context.channel()).isEqualTo("DIRECT_WEBSITE");
        assertThat(context.payer().firstName()).isEqualTo("Madonna");
        assertThat(context.payer().lastName()).isEqualTo("Madonna");
    }

    @Test
    void contextNeverInventsMissingPayerDetails() {
        Parcel p = payable();
        when(identity.currentUser(any())).thenReturn(Optional.of(new IdentityUser(customerId, " ", "Nimal", "077")));
        assertApi(HttpStatus.CONFLICT, "PAYER_DETAILS_MISSING", () -> service.getPaymentContext(p.getId(), jwt(customerId)));

        Parcel noPickup = parcel(BookingStatus.CHECKOUT_DRAFT, "ONLINE");
        noPickup.setCustomerEmail("a@b.lk");
        noPickup.setCustomerName("A B");
        noPickup.setCustomerPhone("077");
        assertApi(HttpStatus.CONFLICT, "PAYER_DETAILS_MISSING", () -> service.getPaymentContext(noPickup.getId(), jwt(customerId)));
    }
}
