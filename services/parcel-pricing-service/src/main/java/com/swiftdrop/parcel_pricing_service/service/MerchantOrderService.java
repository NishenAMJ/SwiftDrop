package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.MerchantContact;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.AddressDto;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateOrderException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidOrderException;
import com.swiftdrop.parcel_pricing_service.exception.OrderNotFoundException;
import com.swiftdrop.parcel_pricing_service.repository.BookingReceiptRepository;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Merchant orders (Member 4). A merchant order is a {@link Parcel} with channel MERCHANT_API and
 * merchantId set, so it shares everything with customer bookings: the SWD tracking number, the
 * {@link PricingService} fee, payment (ONLINE) or immediate confirmation (COD), receipt, booking
 * events, tracking and status updates, cancellation, and the merchant's My Bookings list.
 *
 * <p>The parcel's customerId is the merchant account's user id, so every existing ownership check
 * (payment context, receipt, cancellation, My Bookings) works for merchants unchanged.
 */
@Service
@Transactional(readOnly = true)
public class MerchantOrderService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOrderService.class);

    /** Payment & Settlement accepts DIRECT_WEBSITE and MERCHANT_API; MERCHANT_API requires merchantId. */
    public static final String MERCHANT_CHANNEL = "MERCHANT_API";

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final String REFERENCE_CONSTRAINT = "uq_parcels_merchant_reference";

    private final ParcelRepository parcelRepository;
    private final BookingReceiptRepository receiptRepository;
    private final BookingService bookingService;
    private final ReferenceGenerator referenceGenerator;
    private final IdentityClient identityClient;

    public MerchantOrderService(ParcelRepository parcelRepository,
                                BookingReceiptRepository receiptRepository,
                                BookingService bookingService,
                                ReferenceGenerator referenceGenerator,
                                IdentityClient identityClient) {
        this.parcelRepository = parcelRepository;
        this.receiptRepository = receiptRepository;
        this.bookingService = bookingService;
        this.referenceGenerator = referenceGenerator;
        this.identityClient = identityClient;
    }

    @Transactional
    public MerchantOrderResponse createOrder(MerchantPrincipal merchant, MerchantOrderRequest request, OrderSource source) {
        Objects.requireNonNull(merchant, "merchant");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(source, "source");

        String reference = trimToNull(request.merchantReference());
        validateBusinessRules(request, reference, source);
        if (reference != null && parcelRepository.existsByMerchantIdAndMerchantReference(merchant.merchantId(), reference)) {
            throw duplicate(reference, null);
        }

        String deliveryZone = normalizeZone(request.deliveryAddress().zoneCode());
        if (deliveryZone == null) {
            throw ApiException.badRequest("ZONE_REQUIRED", "deliveryAddress.zoneCode is required. Active zones are listed at GET /api/zones.");
        }
        String pickupZone = normalizeZone(request.pickupAddress().zoneCode());
        if (pickupZone != null) bookingService.requireActiveZone(pickupZone);

        DeliveryType urgency = request.urgency() != null ? request.urgency() : DeliveryType.NORMAL;
        OffsetDateTime now = OffsetDateTime.now();

        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        parcel.setTrackingNumber(referenceGenerator.generateTrackingNumber());
        parcel.setCustomerId(merchant.userId());
        parcel.setMerchantId(merchant.merchantId());
        parcel.setChannel(MERCHANT_CHANNEL);
        parcel.setOrderSource(source);
        parcel.setMerchantReference(reference);
        parcel.setDescription(trimToNull(request.description()));
        parcel.setWeightKg(request.weightKg().setScale(2, RoundingMode.HALF_UP));
        parcel.setUrgency(urgency.name());
        parcel.setUrgentNote(urgency == DeliveryType.URGENT ? trimToNull(request.urgentNote()) : null);
        parcel.setPaymentMethod(request.paymentMethod().name());
        parcel.setCodAmount(request.paymentMethod() == PaymentMethod.COD
                ? request.codAmount().setScale(2, RoundingMode.HALF_UP) : null);
        parcel.setCreatedAt(now);
        parcel.setUpdatedAt(now);
        parcel.setStatusUpdatedAt(now);

        // Same fee as a customer booking: pricing rules and zones managed by dispatchers.
        bookingService.applyQuote(parcel, parcel.getWeightKg(), deliveryZone, urgency);

        parcel.addAddress(toAddress("PICKUP", request.pickupAddress(), pickupZone));
        parcel.addAddress(toAddress("DELIVERY", request.deliveryAddress(), deliveryZone));

        // Payer and receipt name: the merchant's business contact from Identity, else the pickup contact.
        Optional<MerchantContact> contact = identityClient.currentMerchant(merchant.accessToken());
        parcel.setCustomerName(contact.map(MerchantContact::businessName).filter(s -> !s.isBlank())
                .orElse(request.pickupAddress().contactName().trim()));
        parcel.setCustomerEmail(contact.map(MerchantContact::contactEmail).filter(s -> !s.isBlank()).orElse(null));
        parcel.setCustomerPhone(contact.map(MerchantContact::contactPhone).filter(s -> !s.isBlank())
                .orElse(request.pickupAddress().contactPhone().trim()));

        BookingService.NewBooking saved = bookingService.saveNewBooking(parcel);
        try {
            // Surface a concurrent duplicate merchantReference now, as 409, not as a 500 at commit.
            parcelRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (reference != null && isReferenceViolation(ex)) throw duplicate(reference, ex);
            throw ex;
        }

        Parcel order = saved.parcel();
        log.info("Created merchant order parcel={} tracking={} merchant={} source={} payment={}",
                order.getId(), order.getTrackingNumber(), merchant.merchantId(), source, order.getPaymentMethod());
        return MerchantOrderResponse.from(order, saved.receipt());
    }

    public Page<MerchantOrderResponse> getOrders(MerchantPrincipal merchant, int page, int size) {
        Objects.requireNonNull(merchant, "merchant");
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return parcelRepository.findByMerchantId(merchant.merchantId(), pageable)
                .map(p -> MerchantOrderResponse.from(p, receiptRepository.findByParcelId(p.getId()).orElse(null)));
    }

    /** Another merchant's order answers 404, so order ids cannot be probed. */
    public MerchantOrderResponse getOrder(MerchantPrincipal merchant, UUID orderId) {
        Objects.requireNonNull(merchant, "merchant");
        Parcel parcel = parcelRepository.findByIdAndMerchantId(orderId, merchant.merchantId())
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return MerchantOrderResponse.from(parcel, receiptRepository.findByParcelId(parcel.getId()).orElse(null));
    }

    private static void validateBusinessRules(MerchantOrderRequest request, String reference, OrderSource source) {
        if (source == OrderSource.EXTERNAL_API && reference == null) {
            throw new InvalidOrderException("merchantReference is required for orders created through the external API");
        }
        if (request.paymentMethod() == PaymentMethod.COD && request.codAmount() == null) {
            throw new InvalidOrderException("codAmount is required when the payment method is COD");
        }
        if (request.paymentMethod() == PaymentMethod.ONLINE && request.codAmount() != null) {
            throw new InvalidOrderException("codAmount must not be set when the payment method is ONLINE");
        }
    }

    private static DuplicateOrderException duplicate(String reference, Throwable cause) {
        String message = "An order with merchantReference '" + reference + "' already exists";
        return cause == null ? new DuplicateOrderException(message) : new DuplicateOrderException(message, cause);
    }

    static boolean isReferenceViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(REFERENCE_CONSTRAINT);
    }

    private static ParcelAddress toAddress(String type, AddressDto dto, String zoneCode) {
        return new ParcelAddress(type, dto.contactName().trim(), dto.contactPhone().trim(), dto.streetAddress().trim(),
                dto.city().trim(), trimToNull(dto.postalCode()), zoneCode);
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
