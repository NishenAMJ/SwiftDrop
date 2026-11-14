package com.swiftdrop.communication.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.swiftdrop.communication.common.PhoneMasker;
import com.swiftdrop.communication.contact.Contact;
import com.swiftdrop.communication.contact.ContactRepository;
import com.swiftdrop.communication.notification.Notification;
import com.swiftdrop.communication.notification.NotificationRepository;
import com.swiftdrop.communication.notification.NotificationStatus;

@SpringBootTest
class ParcelEventIntegrationTest {

    @Autowired
    private ParcelEventListener listener;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Message createJsonMessage(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    private Contact seedContact(UUID userId, String status, String phoneNumber) {
        Instant now = Instant.now();
        Contact contact = new Contact(
                userId,
                "user-" + userId + "@example.com",
                "Parcel Customer",
                phoneNumber,
                status,
                now,
                now,
                now
        );
        return contactRepository.save(contact);
    }

    @Test
    void outForDeliverySendsBothEmailAndMockSms() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        seedContact(userId, "ACTIVE", "+94771234567");

        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-03T10:15:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SWD-2026-999001",
                    "status": "OUT_FOR_DELIVERY",
                    "previousStatus": "IN_TRANSIT",
                    "customerUserId": "%s",
                    "changedAt": "2026-11-03T10:15:00Z"
                  }
                }
                """.formatted(eventId, parcelId, userId);

        listener.onMessage(createJsonMessage(payload));

        assertTrue(processedEventRepository.existsByEventIdAndConsumer(eventId, ParcelNotificationHandler.CONSUMER));

        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> parcelId.equals(n.getParcelId()))
                .toList();

        assertEquals(2, notifs.size());
        assertTrue(notifs.stream().anyMatch(n -> "EMAIL".equals(n.getChannel()) && n.getStatus() == NotificationStatus.SENT));
        assertTrue(notifs.stream().anyMatch(n -> "SMS".equals(n.getChannel()) && n.getStatus() == NotificationStatus.SENT));

        // Idempotency: replay the same event
        listener.onMessage(createJsonMessage(payload));
        long countAfterReplay = notificationRepository.findAll().stream()
                .filter(n -> parcelId.equals(n.getParcelId()))
                .count();
        assertEquals(2, countAfterReplay);
    }

    @Test
    void pickedUpSendsEmailOnly() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        seedContact(userId, "ACTIVE", "+94771234567");

        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-03T08:30:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SWD-2026-999002",
                    "status": "PICKED_UP",
                    "previousStatus": "CREATED",
                    "customerUserId": "%s",
                    "changedAt": "2026-11-03T08:30:00Z"
                  }
                }
                """.formatted(eventId, parcelId, userId);

        listener.onMessage(createJsonMessage(payload));

        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> parcelId.equals(n.getParcelId()))
                .toList();

        assertEquals(1, notifs.size());
        assertEquals("EMAIL", notifs.getFirst().getChannel());
        assertEquals(NotificationStatus.SENT, notifs.getFirst().getStatus());
    }

    @Test
    void parcelFailedSendsNotificationsWithFailureReason() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        seedContact(userId, "ACTIVE", "+94771234567");

        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-03T16:20:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SWD-2026-999003",
                    "status": "FAILED",
                    "previousStatus": "OUT_FOR_DELIVERY",
                    "customerUserId": "%s",
                    "failureReason": "Customer address unreachable",
                    "changedAt": "2026-11-03T16:20:00Z"
                  }
                }
                """.formatted(eventId, parcelId, userId);

        listener.onMessage(createJsonMessage(payload));

        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> parcelId.equals(n.getParcelId()))
                .toList();

        assertEquals(2, notifs.size());
        assertTrue(notifs.stream().allMatch(n -> n.getStatus() == NotificationStatus.SENT));
    }

    @Test
    void inactiveContactRecordsFailedStatus() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        seedContact(userId, "SUSPENDED", "+94771234567");

        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-03T10:15:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SWD-2026-999004",
                    "status": "OUT_FOR_DELIVERY",
                    "previousStatus": "IN_TRANSIT",
                    "customerUserId": "%s",
                    "changedAt": "2026-11-03T10:15:00Z"
                  }
                }
                """.formatted(eventId, parcelId, userId);

        listener.onMessage(createJsonMessage(payload));

        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> parcelId.equals(n.getParcelId()))
                .toList();

        assertEquals(2, notifs.size());
        assertTrue(notifs.stream().allMatch(n -> n.getStatus() == NotificationStatus.FAILED));
        assertTrue(notifs.stream().allMatch(n -> "Recipient is not active".equals(n.getFailureReason())));
    }

    @Test
    void missingCustomerUserIdOrContactIsSkipped() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        // No customerUserId
        String anonymousPayload = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-03T10:15:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SWD-2026-999005",
                    "status": "OUT_FOR_DELIVERY",
                    "changedAt": "2026-11-03T10:15:00Z"
                  }
                }
                """.formatted(eventId, parcelId);

        listener.onMessage(createJsonMessage(anonymousPayload));
        assertTrue(processedEventRepository.existsByEventIdAndConsumer(eventId, ParcelNotificationHandler.CONSUMER));

        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> parcelId.equals(n.getParcelId()))
                .toList();
        assertTrue(notifs.isEmpty());
    }

    @Test
    void phoneMaskerMasksCorrectly() {
        assertEquals("+9477****567", PhoneMasker.mask("+94771234567"));
        assertEquals("+9477****001", PhoneMasker.mask("+94770000001"));
        assertEquals("****", PhoneMasker.mask(null));
        assertEquals("****", PhoneMasker.mask("12345"));
    }

    @Test
    void unreadableJsonAndMissingEnvelopeFieldsRejectWithoutRequeue() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                listener.onMessage(createJsonMessage("not a valid json")));

        String missingEventId = """
                {
                  "eventType": "parcel.status-changed",
                  "occurredAt": "2026-11-03T10:15:00Z",
                  "data": { "trackingNumber": "SWD-1234" }
                }
                """;
        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                listener.onMessage(createJsonMessage(missingEventId)));
    }
}
