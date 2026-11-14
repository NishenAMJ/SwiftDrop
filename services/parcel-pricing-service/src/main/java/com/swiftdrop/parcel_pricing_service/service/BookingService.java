package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.entity.*;
import com.swiftdrop.parcel_pricing_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public BookingService(ParcelRepository parcelRepository,
                          BookingReceiptRepository receiptRepository,
                          ReferenceGenerator referenceGenerator) {
        this.parcelRepository = parcelRepository;
        this.receiptRepository = receiptRepository;
        this.referenceGenerator = referenceGenerator;
    }

    public BookingResponseDto createBooking(CreateBookingRequest request, UUID customerId) {
        if (customerId == null) {
            customerId = UUID.randomUUID();
        }

        String urgency = (request.urgency() != null && !request.urgency().isBlank())
                ? request.urgency().trim().toUpperCase()
                : "NORMAL";

        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        String trackingNumber = referenceGenerator.generateTrackingNumber();
        parcel.setTrackingNumber(trackingNumber);
        parcel.setCustomerId(customerId);
        parcel.setDescription(request.description());
        parcel.setWeightKg(request.weightKg());
        parcel.setUrgency(urgency);
        parcel.setStatus("BOOKED");
        parcel.setCreatedAt(OffsetDateTime.now());
        parcel.setUpdatedAt(OffsetDateTime.now());

        // Estimated fee formula (Base 350 + 150/kg + 300 if Urgent)
        // Note: Designed so Member 4 can seamlessly inject their dynamic PricingService here
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
}

