package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.DispatchParcelDto;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Read-only parcel lookup for dispatch, used by Delivery Operations when a courier is assigned. */
@Service
public class DispatchParcelService {

    private final ParcelRepository parcelRepository;

    public DispatchParcelService(ParcelRepository parcelRepository) {
        this.parcelRepository = parcelRepository;
    }

    @Transactional(readOnly = true)
    public DispatchParcelDto findByTrackingNumber(String trackingNumber) {
        String reference = trackingNumber == null ? "" : trackingNumber.trim().toUpperCase(Locale.ROOT);
        // Unpaid drafts are not real bookings yet, so they cannot be dispatched.
        return parcelRepository.findByTrackingNumber(reference)
                .filter(BookingService::isVisibleBooking)
                .map(DispatchParcelDto::from)
                .orElseThrow(() -> ApiException.notFound("No booking has this tracking number."));
    }
}
