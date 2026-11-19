package com.swiftdrop.parcel_pricing_service.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Stub service for sending webhook notifications.
 * In a real system this would POST to an external notification endpoint.
 */
@Service
public class NotificationWebhookService {

    public void notifyPaymentFailure(UUID customerId, UUID bookingId, String reason) {
        // Stub: log or send webhook about payment failure.
    }

    public void notifyCancellation(UUID customerId, UUID bookingId, String reason) {
        // Stub: notify external system that a booking was cancelled.
    }
}
