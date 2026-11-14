package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.entity.*;
import com.swiftdrop.parcel_pricing_service.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class BookingService {

    private final ParcelRepository parcelRepository;
    private final BookingReceiptRepository receiptRepository;
    private final ReferenceGenerator referenceGenerator;
    private final CustomerProfileRepository profileRepository;

    public BookingService(ParcelRepository parcelRepository,
                          BookingReceiptRepository receiptRepository,
                          ReferenceGenerator referenceGenerator) {
        this(parcelRepository, receiptRepository, referenceGenerator, null);
    }

    @Autowired
    public BookingService(ParcelRepository parcelRepository,
                          BookingReceiptRepository receiptRepository,
                          ReferenceGenerator referenceGenerator,
                          @Autowired(required = false) CustomerProfileRepository profileRepository) {
        this.parcelRepository = parcelRepository;
        this.receiptRepository = receiptRepository;
        this.referenceGenerator = referenceGenerator;
        this.profileRepository = profileRepository;
    }

    public BookingResponseDto createBooking(CreateBookingRequest request, UUID customerId) {
        if (customerId == null) {
            customerId = UUID.randomUUID();
        }

        String urgency = (request.urgency() != null && !request.urgency().isBlank())
                ? request.urgency().trim().toUpperCase()
                : "NORMAL";

        String paymentMethod = (request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod().trim().toUpperCase()
                : "ONLINE";

        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        String trackingNumber = referenceGenerator.generateTrackingNumber();
        parcel.setTrackingNumber(trackingNumber);
        parcel.setCustomerId(customerId);
        parcel.setDescription(request.description());
        parcel.setWeightKg(request.weightKg());
        parcel.setUrgency(urgency);
        parcel.setStatus("BOOKED");
        parcel.setPaymentMethod(paymentMethod);
        parcel.setChannel("DIRECT_WEBSITE");
        parcel.setCreatedAt(OffsetDateTime.now());
        parcel.setUpdatedAt(OffsetDateTime.now());

        // Generate immutable pricing snapshot ID & expiration (valid for 24 hours)
        String snapshotId = "SNP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        parcel.setPricingSnapshotId(snapshotId);
        parcel.setPricingExpiresAt(OffsetDateTime.now().plusHours(24));

        // Estimated fee formula (Base 350 + 150/kg + 300 if Urgent)
        BigDecimal baseFee = BigDecimal.valueOf(350);
        BigDecimal weightCharge = request.weightKg().multiply(BigDecimal.valueOf(150));
        BigDecimal urgentCharge = "URGENT".equals(urgency) ? BigDecimal.valueOf(300) : BigDecimal.ZERO;
        BigDecimal totalFee = baseFee.add(weightCharge).add(urgentCharge);
        parcel.setEstimatedFee(totalFee);

        // Add Pickup and Delivery addresses
        if (request.pickupAddress() != null) {
            ParcelAddress pickup = new ParcelAddress(
                "PICKUP",
                request.pickupAddress().contactName(),
                request.pickupAddress().contactPhone(),
                request.pickupAddress().streetAddress(),
                request.pickupAddress().city(),
                request.pickupAddress().postalCode(),
                request.pickupAddress().zoneCode()
            );
            parcel.addAddress(pickup);
        }

        if (request.deliveryAddress() != null) {
            ParcelAddress delivery = new ParcelAddress(
                "DELIVERY",
                request.deliveryAddress().contactName(),
                request.deliveryAddress().contactPhone(),
                request.deliveryAddress().streetAddress(),
                request.deliveryAddress().city(),
                request.deliveryAddress().postalCode(),
                request.deliveryAddress().zoneCode()
            );
            parcel.addAddress(delivery);
        }

        Parcel savedParcel = parcelRepository.save(parcel);

        // Generate and save receipt
        String receiptNumber = referenceGenerator.generateReceiptNumber(trackingNumber);
        BookingReceipt receipt = new BookingReceipt(receiptNumber, savedParcel, totalFee, "LKR");
        BookingReceipt savedReceipt = receiptRepository.save(receipt);

        return BookingResponseDto.from(savedParcel, savedReceipt);
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryDto> getCustomerBookings(UUID customerId) {
        return parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(BookingSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getBookingById(UUID id) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Parcel not found with id: " + id));
        BookingReceipt receipt = receiptRepository.findByParcelId(id).orElse(null);
        return BookingResponseDto.from(parcel, receipt);
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getBookingByTrackingNumber(String trackingNumber) {
        Parcel parcel = parcelRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NoSuchElementException("Parcel not found with tracking number: " + trackingNumber));
        BookingReceipt receipt = receiptRepository.findByParcelId(parcel.getId()).orElse(null);
        return BookingResponseDto.from(parcel, receipt);
    }

    @Transactional(readOnly = true)
    public BookingReceiptDto getReceiptByParcelId(UUID parcelId) {
        BookingReceipt receipt = receiptRepository.findByParcelId(parcelId)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found for parcel: " + parcelId));
        return BookingReceiptDto.from(receipt);
    }

    /**
     * Resolves the authoritative payment context for Payment Service.
     * Requires valid Identity JWT and enforces strict customer/merchant ownership.
     */
    @Transactional(readOnly = true)
    public PaymentContextResponse getPaymentContext(UUID bookingId, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid authorization token");
        }

        Parcel parcel = parcelRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: " + bookingId));

        // 1. Authorize caller
        String subject = jwt.getSubject();
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean isDispatcher = roles != null && (roles.contains("DISPATCHER") || roles.contains("ROLE_DISPATCHER")
                || roles.contains("ADMIN") || roles.contains("ROLE_ADMIN"));

        if (!isDispatcher) {
            try {
                UUID callerId = UUID.fromString(subject);
                if (!callerId.equals(parcel.getCustomerId())) {
                    if (parcel.getMerchantId() == null || !callerId.equals(parcel.getMerchantId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: caller does not own this booking");
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: invalid token subject");
            }
        }

        // 2. Payable eligibility
        if ("CANCELLED".equalsIgnoreCase(parcel.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is cancelled and cannot be paid");
        }

        boolean isEligibleStatus = "BOOKED".equalsIgnoreCase(parcel.getStatus()) || "PENDING_PAYMENT".equalsIgnoreCase(parcel.getStatus());
        boolean isOnlinePayment = !"COD".equalsIgnoreCase(parcel.getPaymentMethod());
        boolean isNotExpired = parcel.getPricingExpiresAt() == null || parcel.getPricingExpiresAt().isAfter(OffsetDateTime.now());
        boolean payable = isEligibleStatus && isOnlinePayment && isNotExpired;

        // 3. Payer billing details from CustomerProfile
        CustomerProfile profile = profileRepository != null
                ? profileRepository.findById(parcel.getCustomerId()).orElse(null)
                : null;

        String fullName = (profile != null && profile.getFullName() != null) ? profile.getFullName() : "Customer";
        String[] nameParts = fullName.trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "Customer";

        String email = (profile != null && profile.getEmail() != null) ? profile.getEmail() : "customer@swiftdrop.lk";
        String phone = (profile != null && profile.getPhoneNumber() != null) ? profile.getPhoneNumber() : "+94770000000";
        String address = (profile != null && profile.getStreetAddress() != null) ? profile.getStreetAddress() : "Colombo";
        String city = (profile != null && profile.getCity() != null) ? profile.getCity() : "Colombo";

        PaymentContextResponse.PayerDetailsDto payer = new PaymentContextResponse.PayerDetailsDto(
                firstName,
                lastName,
                email,
                phone,
                address,
                city,
                "Sri Lanka"
        );

        String snapshotId = parcel.getPricingSnapshotId() != null
                ? parcel.getPricingSnapshotId()
                : "SNP-" + parcel.getId().toString().substring(0, 8).toUpperCase();

        return new PaymentContextResponse(
                parcel.getId().toString(),
                parcel.getChannel() != null ? parcel.getChannel() : "DIRECT_WEBSITE",
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                payable,
                snapshotId,
                parcel.getEstimatedFee(),
                "LKR",
                payer
        );
    }
}
