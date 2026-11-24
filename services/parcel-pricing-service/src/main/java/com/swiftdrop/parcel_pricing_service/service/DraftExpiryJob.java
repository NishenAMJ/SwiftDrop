package com.swiftdrop.parcel_pricing_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks unpaid online checkouts as EXPIRED once their 24-hour price is no longer valid.
 * They are never deleted: Payment may still hold a pending payment for them, and a payment that
 * succeeds late still confirms the booking.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.bookings.draft-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class DraftExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(DraftExpiryJob.class);

    private final BookingService bookingService;

    public DraftExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.bookings.draft-expiry.delay-ms:900000}",
            initialDelayString = "${swiftdrop.bookings.draft-expiry.initial-delay-ms:60000}")
    public void expireDrafts() {
        int expired = bookingService.expireStaleDrafts();
        if (expired > 0) log.info("Marked {} unpaid checkout(s) as EXPIRED", expired);
    }
}
