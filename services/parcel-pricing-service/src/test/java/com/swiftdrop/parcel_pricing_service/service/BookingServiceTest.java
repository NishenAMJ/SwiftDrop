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
        when(referenceGenerator.generateReceiptNumber("SWD-2026-000002")).thenReturn("RCP-2026-000002");

        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.save(any(BookingReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        p.setWeightKg(new BigDecimal("3.50"));
        p.setEstimatedFee(new BigDecimal("875.00"));

        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(p));

        List<BookingSummaryDto> result = bookingService.getCustomerBookings(customerId);

        assertEquals(1, result.size());
        assertEquals("SWD-2026-000003", result.get(0).trackingNumber());
    }
}

