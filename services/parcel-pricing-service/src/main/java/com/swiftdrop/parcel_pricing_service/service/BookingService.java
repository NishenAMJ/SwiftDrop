package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier.VerifiedPayment;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.entity.*;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Customer bookings (Member 3): create, price, pay, confirm, list and receipt.
 *
 * <p>Online flow: the booking is saved as CHECKOUT_DRAFT so Payment can read its amount, but it
 * is hidden from history, has no receipt and is not sent to other services until a verified
 * payment confirms it. Confirmation can come from the customer's browser (confirm-payment) or
 * from the payment.succeeded event, whichever arrives first; both are safe to repeat.
 */
@Service
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String CURRENCY = "LKR";
    private static final long CHECKOUT_VALID_HOURS = 24;

    private final ParcelRepository parcelRepository;
    private final BookingReceiptRepository receiptRepository;
    private final ReferenceGenerator referenceGenerator;
    private final PricingService pricingService;
    private final ZoneRepository zoneRepository;
    private final IdentityClient identityClient;
    private final ParcelEventPublisher events;

    public BookingService(ParcelRepository parcelRepository,
                          BookingReceiptRepository receiptRepository,
                          ReferenceGenerator referenceGenerator,
                          PricingService pricingService,
                          ZoneRepository zoneRepository,
                          IdentityClient identityClient,
                          ParcelEventPublisher events) {
        this.parcelRepository = parcelRepository;
        this.receiptRepository = receiptRepository;
        this.referenceGenerator = referenceGenerator;
        this.pricingService = pricingService;
        this.zoneRepository = zoneRepository;
        this.identityClient = identityClient;
        this.events = events;
    }

    // ---------------------------------------------------------------- create

    public BookingResponseDto createBooking(CreateBookingRequest request, Jwt jwt) {
        UUID customerId = customerIdOf(jwt);
        DeliveryType urgency = parseUrgency(request.urgency());
        PaymentMethod paymentMethod = parsePaymentMethod(request.paymentMethod());

        String deliveryZone = normalizeZone(request.deliveryAddress().zoneCode());
        if (deliveryZone == null) {
            throw ApiException.badRequest("ZONE_REQUIRED", "Choose the delivery zone for the recipient's address.");
        }
        String pickupZone = normalizeZone(request.pickupAddress().zoneCode());
        if (pickupZone != null) requireActiveZone(pickupZone);

        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        parcel.setTrackingNumber(referenceGenerator.generateTrackingNumber());
        parcel.setCustomerId(customerId);
        parcel.setDescription(trimToNull(request.description()));
        parcel.setWeightKg(request.weightKg());
        parcel.setUrgency(urgency.name());
        parcel.setPaymentMethod(paymentMethod.name());
        parcel.setChannel("DIRECT_WEBSITE");
        parcel.setCreatedAt(OffsetDateTime.now());
        parcel.setUpdatedAt(OffsetDateTime.now());
        parcel.setStatusUpdatedAt(OffsetDateTime.now());

        // The single source of truth for prices: the pricing rules and zones managed by dispatchers.
        applyQuote(parcel, request.weightKg(), deliveryZone, urgency);

        parcel.addAddress(toAddress("PICKUP", request.pickupAddress(), pickupZone));
        parcel.addAddress(toAddress("DELIVERY", request.deliveryAddress(), deliveryZone));

        // Who booked it, from Identity (the owner of name/email/phone). Used on the receipt and as payer.
        Optional<IdentityUser> account = identityClient.currentUser(jwt);
        parcel.setCustomerName(account.map(IdentityUser::fullName).filter(s -> !s.isBlank())
                .orElse(request.pickupAddress().contactName().trim()));
        parcel.setCustomerEmail(account.map(IdentityUser::email).orElse(null));
        parcel.setCustomerPhone(account.map(IdentityUser::phoneNumber).filter(s -> !s.isBlank())
                .orElse(request.pickupAddress().contactPhone().trim()));

        NewBooking saved = saveNewBooking(parcel);
        return BookingResponseDto.from(saved.parcel(), saved.receipt());
    }

    /** A just-saved booking and its receipt (null until an ONLINE booking is paid). */
    public record NewBooking(Parcel parcel, BookingReceipt receipt) {
    }

    /**
     * Prices a new parcel with {@link PricingService} (the only fee formula) and copies the quote and
     * breakdown onto it. Used by customer bookings and merchant orders alike.
     */
    public PricingResponse applyQuote(Parcel parcel, BigDecimal weightKg, String deliveryZone, DeliveryType urgency) {
        PricingResponse quote = pricingService.calculateFee(new PricingRequest(
                weightKg, deliveryZone, null, urgency == DeliveryType.URGENT, null));
        parcel.setEstimatedFee(quote.totalDeliveryFee());
        parcel.setBaseFee(quote.baseFee());
        parcel.setWeightCharge(quote.weightCharge());
        parcel.setZoneCharge(quote.zoneCharge());
        parcel.setUrgentSurcharge(quote.urgentSurcharge());
        parcel.setPricingRuleCode(quote.appliedRuleCode());
        parcel.setPricingZoneCode(quote.zoneCode());
        parcel.setPricingSnapshotId("SNP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        parcel.setPricingExpiresAt(OffsetDateTime.now().plusHours(CHECKOUT_VALID_HOURS));
        return quote;
    }

    /**
     * Saves a new, priced parcel and starts its lifecycle. COD is confirmed at once (receipt and
     * booking events); ONLINE waits as CHECKOUT_DRAFT until a verified payment confirms it.
     * Shared by customer bookings and merchant orders, so both behave exactly the same.
     */
    public NewBooking saveNewBooking(Parcel parcel) {
        if (PaymentMethod.COD.name().equals(parcel.getPaymentMethod())) {
            parcel.setStatus(BookingStatus.BOOKED);
            parcel.setPaymentStatus("PENDING");
            Parcel saved = parcelRepository.save(parcel);
            BookingReceipt receipt = issueReceipt(saved);
            events.bookingConfirmed(saved);
            return new NewBooking(saved, receipt);
        }
        parcel.setStatus(BookingStatus.CHECKOUT_DRAFT);
        parcel.setPaymentStatus("UNPAID");
        return new NewBooking(parcelRepository.save(parcel), null);
    }

    // ---------------------------------------------------------------- payment confirmation

    /** Called from the browser after the customer returns from the payment page. */
    public BookingResponseDto confirmPaid(UUID id, UUID customerId, UUID paymentId, VerifiedPayment payment) {
        Parcel parcel = parcelRepository.lockById(id).orElseThrow(() -> ApiException.notFound("Booking was not found."));
        if (!customerId.equals(parcel.getCustomerId())) throw ApiException.forbidden("You do not own this booking.");
        if (!id.toString().equals(payment.bookingId()) || !"SUCCESS".equals(payment.status())
                || payment.providerReference() == null || payment.providerReference().isBlank()) {
            throw ApiException.conflict("PAYMENT_NOT_VERIFIED", "A matching successful payment is required.");
        }
        return finalizePayment(parcel, paymentId, payment.amount(), payment.currency());
    }

    /**
     * Called when Payment publishes payment.succeeded. This confirms the booking even if the
     * customer closed the browser before returning from the payment page.
     */
    public void confirmPaidFromEvent(String bookingId, UUID paymentId, BigDecimal amount, String currency) {
        UUID id = parseUuid(bookingId);
        if (id == null) return;
        Optional<Parcel> found = parcelRepository.lockById(id);
        if (found.isEmpty()) {
            log.info("payment.succeeded for unknown booking {} ignored", bookingId);
            return;
        }
        try {
            finalizePayment(found.get(), paymentId, amount, currency);
        } catch (ApiException ex) {
            // A real payment that does not match needs a person, not an endless retry.
            log.error("payment.succeeded {} could not confirm booking {}: {}", paymentId, bookingId, ex.getMessage());
        }
    }

    private BookingResponseDto finalizePayment(Parcel parcel, UUID paymentId, BigDecimal amount, String currency) {
        if (!PaymentMethod.ONLINE.name().equals(parcel.getPaymentMethod()) || !CURRENCY.equals(currency)
                || amount == null || amount.compareTo(parcel.getEstimatedFee()) != 0) {
            throw ApiException.conflict("PAYMENT_NOT_VERIFIED", "The payment does not match this booking's amount.");
        }
        if (isPaid(parcel)) {
            return BookingResponseDto.from(parcel, receiptRepository.findByParcelId(parcel.getId()).orElse(null));
        }
        if (!BookingStatus.UNPAID_DRAFTS.contains(parcel.getStatus())) {
            throw ApiException.conflict("INVALID_BOOKING_STATE", "This booking cannot be confirmed.");
        }
        // An expired or abandoned checkout that was still paid is confirmed: the money was taken.
        parcel.setStatus(BookingStatus.BOOKED);
        parcel.setPaymentStatus("PAID");
        parcel.setPaymentId(paymentId);
        parcel.setPaidAt(OffsetDateTime.now());
        parcel.setUpdatedAt(OffsetDateTime.now());
        parcel.setStatusUpdatedAt(OffsetDateTime.now());
        BookingReceipt receipt = issueReceipt(parcel);
        events.bookingConfirmed(parcel);
        return BookingResponseDto.from(parcel, receipt);
    }

    /** Verified full customer cash receipt; delivery status and merchant balances are unaffected. */
    public void confirmCustomerCod(String bookingId, UUID customerId, String snapshot, BigDecimal amount,
                                   String currency, String collectedAt) {
        UUID id = parseUuid(bookingId);
        Parcel parcel = id == null ? null : parcelRepository.lockById(id).orElse(null);
        if (parcel == null || !"DIRECT_WEBSITE".equals(parcel.getChannel()) || parcel.getMerchantId() != null
                || !"COD".equals(parcel.getPaymentMethod()) || BookingStatus.CANCELLED.equals(parcel.getStatus())
                || !isVisibleBooking(parcel) || customerId == null || !customerId.equals(parcel.getCustomerId())
                || snapshot == null || !snapshot.equals(parcel.getPricingSnapshotId()) || !"LKR".equals(currency)
                || amount == null || parcel.getEstimatedFee() == null || amount.compareTo(parcel.getEstimatedFee()) != 0)
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Customer COD receipt does not match booking");
        OffsetDateTime paidAt;
        try { paidAt = OffsetDateTime.parse(collectedAt); }
        catch (RuntimeException invalid) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Invalid COD collection time", invalid);
        }
        if (isPaid(parcel)) return;
        parcel.setPaymentStatus("PAID");
        parcel.setPaidAt(paidAt);
        parcel.setUpdatedAt(OffsetDateTime.now());
    }

    /** payment.failed: keep the draft so the customer can retry, but record the failure. */
    public void markPaymentFailed(String bookingId) {
        UUID id = parseUuid(bookingId);
        if (id == null) return;
        parcelRepository.lockById(id).ifPresent(parcel -> {
            if (BookingStatus.UNPAID_DRAFTS.contains(parcel.getStatus()) && !isPaid(parcel)) {
                parcel.setPaymentStatus("FAILED");
                parcel.setUpdatedAt(OffsetDateTime.now());
            }
        });
    }

    /** payment.refunded: completes the refund side of a cancellation. */
    public void markRefunded(String bookingId, String paymentStatus, UUID paymentId, BigDecimal refundedAmount) {
        UUID id = parseUuid(bookingId);
        if (id == null) return;
        parcelRepository.lockById(id).ifPresent(parcel -> {
            if (!java.util.Objects.equals(parcel.getPaymentId(), paymentId) || paymentId == null
                    || refundedAmount == null || refundedAmount.signum() < 0
                    || !java.util.Set.of("PARTIALLY_REFUNDED", "REFUNDED").contains(paymentStatus)) return;
            // Full refunds must never regress on an older partial-refund event.
            if (!"REFUNDED".equals(parcel.getPaymentStatus())) parcel.setPaymentStatus(paymentStatus);
            if (BookingStatus.CANCELLED.equals(parcel.getStatus()) && parcel.getRefundAmount() != null
                    && refundedAmount.compareTo(parcel.getRefundAmount()) >= 0) parcel.setCancellationStatus("REFUNDED");
            parcel.setUpdatedAt(OffsetDateTime.now());
        });
    }

    /** The customer gave up on an unpaid checkout. A payment that still succeeds later is honoured. */
    public void discardDraft(UUID id, UUID customerId) {
        Parcel parcel = parcelRepository.lockById(id).orElseThrow(() -> ApiException.notFound("Booking was not found."));
        if (!customerId.equals(parcel.getCustomerId())) throw ApiException.forbidden("You do not own this booking.");
        if (!BookingStatus.CHECKOUT_DRAFT.equals(parcel.getStatus()) && !BookingStatus.EXPIRED.equals(parcel.getStatus())) {
            throw ApiException.conflict("NOT_A_DRAFT", "Only an unpaid checkout can be discarded.");
        }
        parcel.setStatus(BookingStatus.ABANDONED);
        parcel.setUpdatedAt(OffsetDateTime.now());
    }

    /** Unpaid checkouts older than their price validity are marked EXPIRED (see DraftExpiryJob). */
    public int expireStaleDrafts() {
        return parcelRepository.expireDrafts(OffsetDateTime.now());
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public List<BookingSummaryDto> getCustomerBookings(UUID customerId) {
        return parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .filter(BookingService::isVisibleBooking)
                .map(BookingSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getBookingById(UUID id) {
        Parcel parcel = parcelRepository.findById(id).orElseThrow(() -> ApiException.notFound("Booking was not found."));
        return BookingResponseDto.from(parcel, receiptRepository.findByParcelId(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getOwnedBooking(UUID id, UUID customerId) {
        Parcel parcel = owned(id, customerId);
        return BookingResponseDto.from(parcel, receiptRepository.findByParcelId(id).orElse(null));
    }

    /**
     * The booker's own booking by tracking number (full details). Anyone else, including the
     * receiver, uses the public tracking page of Tracking & Reporting, which shows no personal data.
     * A booking that is not yours answers 404, so tracking numbers cannot be probed.
     */
    @Transactional(readOnly = true)
    public BookingResponseDto getOwnedBookingByTrackingNumber(String trackingNumber, UUID customerId) {
        String reference = trackingNumber == null ? "" : trackingNumber.trim().toUpperCase(Locale.ROOT);
        Parcel parcel = parcelRepository.findByTrackingNumber(reference)
                .filter(p -> customerId.equals(p.getCustomerId()) && isVisibleBooking(p))
                .orElseThrow(() -> ApiException.notFound("No booking of yours has this tracking number."));
        return BookingResponseDto.from(parcel, receiptRepository.findByParcelId(parcel.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public BookingReceiptDto getReceipt(UUID parcelId, UUID customerId) {
        Parcel parcel = owned(parcelId, customerId);
        if (!isVisibleBooking(parcel)) {
            throw ApiException.conflict("PAYMENT_REQUIRED", "A receipt is issued only after the online payment is verified.");
        }
        BookingReceipt receipt = receiptRepository.findByParcelId(parcelId)
                .orElseThrow(() -> ApiException.notFound("Receipt was not found for this booking."));
        return BookingReceiptDto.from(receipt);
    }

    // ---------------------------------------------------------------- payment context (for Payment service)

    /**
     * The authoritative amount and payer for Payment & Settlement. Payment forwards the customer's
     * token, so the same ownership rules apply. Payer details are real: the customer's Identity
     * account (name, email, phone) and the pickup address. Nothing is invented.
     */
    @Transactional(readOnly = true)
    public PaymentContextResponse getPaymentContext(UUID bookingId, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw ApiException.unauthorized("A valid login is required.");
        Parcel parcel = parcelRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking was not found."));

        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean isDispatcher = roles != null && roles.contains("DISPATCHER");
        if (!isDispatcher) {
            UUID callerId = parseUuid(jwt.getSubject());
            boolean owner = callerId != null && (callerId.equals(parcel.getCustomerId())
                    || (parcel.getMerchantId() != null && callerId.equals(parcel.getMerchantId())));
            if (!owner) throw ApiException.forbidden("You do not own this booking.");
        }

        boolean notExpired = parcel.getPricingExpiresAt() == null || parcel.getPricingExpiresAt().isAfter(OffsetDateTime.now());
        boolean payable = BookingStatus.CHECKOUT_DRAFT.equals(parcel.getStatus())
                && PaymentMethod.ONLINE.name().equals(parcel.getPaymentMethod())
                && notExpired && !isPaid(parcel);

        PaymentContextResponse.PayerDetailsDto payer = payerFor(parcel, jwt);

        return new PaymentContextResponse(
                parcel.getId().toString(),
                parcel.getChannel() != null ? parcel.getChannel() : "DIRECT_WEBSITE",
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                payable,
                parcel.getPricingSnapshotId() != null ? parcel.getPricingSnapshotId()
                        : "SNP-" + parcel.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                parcel.getEstimatedFee(),
                CURRENCY,
                payer);
    }

    private PaymentContextResponse.PayerDetailsDto payerFor(Parcel parcel, Jwt jwt) {
        String name = parcel.getCustomerName();
        String email = parcel.getCustomerEmail();
        String phone = parcel.getCustomerPhone();
        if (email == null || name == null || phone == null) {
            Optional<IdentityUser> account = jwt.getSubject().equals(String.valueOf(parcel.getCustomerId()))
                    ? identityClient.currentUser(jwt) : Optional.empty();
            if (name == null) name = account.map(IdentityUser::fullName).orElse(null);
            if (email == null) email = account.map(IdentityUser::email).orElse(null);
            if (phone == null) phone = account.map(IdentityUser::phoneNumber).orElse(null);
        }
        ParcelAddress pickup = parcel.getPickupAddress();
        if (phone == null && pickup != null) phone = pickup.getContactPhone();
        if (name == null && pickup != null) name = pickup.getContactName();
        if (email == null || email.isBlank() || name == null || name.isBlank() || phone == null || pickup == null) {
            throw ApiException.conflict("PAYER_DETAILS_MISSING",
                    "Your account email or contact details are missing. Update your profile and try again.");
        }
        String[] parts = name.trim().split("\\s+", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : parts[0];
        return new PaymentContextResponse.PayerDetailsDto(firstName, lastName, email, phone,
                pickup.getStreetAddress(), pickup.getCity(), "Sri Lanka");
    }

    // ---------------------------------------------------------------- helpers

    private Parcel owned(UUID id, UUID customerId) {
        Parcel parcel = parcelRepository.findById(id).orElseThrow(() -> ApiException.notFound("Booking was not found."));
        if (!customerId.equals(parcel.getCustomerId())) throw ApiException.forbidden("You do not own this booking.");
        return parcel;
    }

    public BookingReceipt issueReceipt(Parcel parcel) {
        return receiptRepository.findByParcelId(parcel.getId()).orElseGet(() -> receiptRepository.save(new BookingReceipt(
                referenceGenerator.generateReceiptNumber(parcel.getTrackingNumber()), parcel, parcel.getEstimatedFee(), CURRENCY)));
    }

    static boolean isPaid(Parcel parcel) {
        return parcel.getPaidAt() != null || "PAID".equals(parcel.getPaymentStatus());
    }

    /** Shown in history and receipts: COD bookings, and ONLINE bookings whose payment was verified. */
    static boolean isVisibleBooking(Parcel parcel) {
        if (parcel.getStatus() == null || BookingStatus.UNPAID_DRAFTS.contains(parcel.getStatus())) return false;
        String paymentStatus = parcel.getPaymentStatus();
        return PaymentMethod.COD.name().equals(parcel.getPaymentMethod()) || isPaid(parcel)
                || (paymentStatus != null && Set.of("REFUNDED", "PARTIALLY_REFUNDED").contains(paymentStatus));
    }

    public void requireActiveZone(String code) {
        DeliveryZone zone = zoneRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.badRequest("INVALID_ZONE", "Unknown delivery zone: " + code));
        if (zone.getStatus() != ZoneStatus.ACTIVE) {
            throw ApiException.badRequest("INACTIVE_ZONE", "This zone is not served at the moment: " + code);
        }
    }

    private static ParcelAddress toAddress(String type, AddressDto dto, String zoneCode) {
        return new ParcelAddress(type, dto.contactName().trim(), dto.contactPhone().trim(), dto.streetAddress().trim(),
                dto.city().trim(), trimToNull(dto.postalCode()), zoneCode);
    }

    private static DeliveryType parseUrgency(String value) {
        if (value == null || value.isBlank()) return DeliveryType.NORMAL;
        try {
            return DeliveryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_URGENCY", "Urgency must be NORMAL or URGENT.");
        }
    }

    private static PaymentMethod parsePaymentMethod(String value) {
        if (value == null || value.isBlank()) return PaymentMethod.ONLINE;
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD", "Choose ONLINE or COD.");
        }
    }

    static UUID customerIdOf(Jwt jwt) {
        UUID id = jwt == null ? null : parseUuid(jwt.getSubject());
        if (id == null) throw ApiException.unauthorized("A valid customer login is required.");
        return id;
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeZone(String zone) {
        String value = trimToNull(zone);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
