package com.swiftdrop.communication.event;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;

import com.swiftdrop.communication.config.RabbitConfig;
import com.swiftdrop.communication.contact.Contact;
import com.swiftdrop.communication.contact.ContactRepository;
import com.swiftdrop.communication.notification.Notification;
import com.swiftdrop.communication.notification.NotificationRepository;
import com.swiftdrop.communication.notification.NotificationStatus;
import com.swiftdrop.communication.support.AbstractIntegrationTest;

class IdentityEventIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IdentityEventListener listener;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

    @Test
    void fullEventFlowViaRabbitTemplateWithMailVerificationAndIdempotency() {
        reset(mailSender);

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "user.registered",
                  "version": 1,
                  "occurredAt": "2026-11-01T09:30:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "userId": "%s",
                    "email": "flow-%s@example.com",
                    "fullName": "Flow Customer",
                    "phoneNumber": "+94771234567",
                    "roles": ["CUSTOMER"],
                    "status": "ACTIVE",
                    "authProvider": "PASSWORD"
                  }
                }
                """.formatted(eventId, userId, UUID.randomUUID());

        // 1. Publish to exchange
        rabbitTemplate.convertAndSend(RabbitConfig.IDENTITY_EXCHANGE, "user.registered", payload);

        // 2. Await asynchronous consumption and assertions
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertTrue(contactRepository.findById(userId).isPresent());
            List<Notification> notifications = notificationRepository.findAll().stream()
                    .filter(n -> userId.equals(n.getRecipientUserId()) && "WELCOME".equals(n.getTemplate()))
                    .toList();
            assertEquals(1, notifications.size());
            assertEquals(NotificationStatus.SENT, notifications.getFirst().getStatus());
        });

        // 3. Verify mailSender was invoked with SimpleMailMessage
        verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));

        // 4. Publish exact same event again -> must be skipped as duplicate
        rabbitTemplate.convertAndSend(RabbitConfig.IDENTITY_EXCHANGE, "user.registered", payload);

        // Verify still only 1 notification row in DB
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            long count = notificationRepository.findAll().stream()
                    .filter(n -> userId.equals(n.getRecipientUserId()) && "WELCOME".equals(n.getTemplate()))
                    .count();
            assertEquals(1, count);
        });
    }

    @Test
    void userRegisteredCreatesContactSendsWelcomeAndRecordsEvent() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "user.registered",
                  "version": 1,
                  "occurredAt": "2026-11-01T09:30:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "userId": "%s",
                    "email": "test-%s@example.com",
                    "fullName": "Integration Tester",
                    "phoneNumber": "+94771234567",
                    "roles": ["CUSTOMER"],
                    "status": "ACTIVE",
                    "authProvider": "PASSWORD"
                  }
                }
                """.formatted(eventId, userId, UUID.randomUUID());

        listener.onMessage(createJsonMessage(payload));

        assertTrue(contactRepository.findById(userId).isPresent());
        Contact contact = contactRepository.findById(userId).get();
        assertEquals("Integration Tester", contact.getFullName());
        assertEquals("ACTIVE", contact.getStatus());

        assertTrue(processedEventRepository.existsByEventIdAndConsumer(eventId, IdentityEventHandler.CONSUMER));

        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUserId().equals(userId))
                .toList();
        assertEquals(1, notifications.size());
        assertEquals("WELCOME", notifications.getFirst().getTemplate());
        assertEquals(NotificationStatus.SENT, notifications.getFirst().getStatus());

        // Idempotency: replay the same event
        listener.onMessage(createJsonMessage(payload));
        long countAfterReplay = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUserId().equals(userId))
                .count();
        assertEquals(1, countAfterReplay);
    }

    @Test
    void userUpdatedRespectsOccurredAtOrdering() {
        UUID userId = UUID.randomUUID();
        Instant time1 = Instant.parse("2026-11-01T10:00:00Z");
        Instant time2 = Instant.parse("2026-11-01T12:00:00Z");
        Instant olderTime = Instant.parse("2026-11-01T08:00:00Z");

        // Initial registration
        String reg = """
                {
                  "eventId": "%s",
                  "eventType": "user.registered",
                  "version": 1,
                  "occurredAt": "%s",
                  "producer": "identity-merchant-service",
                  "data": {
                    "userId": "%s",
                    "email": "order-%s@example.com",
                    "fullName": "Original Name",
                    "phoneNumber": "+94770000001",
                    "roles": ["CUSTOMER"],
                    "status": "ACTIVE"
                  }
                }
                """.formatted(UUID.randomUUID(), time1, userId, UUID.randomUUID());
        listener.onMessage(createJsonMessage(reg));

        // Update to newer name
        String newerUpdate = """
                {
                  "eventId": "%s",
                  "eventType": "user.updated",
                  "version": 1,
                  "occurredAt": "%s",
                  "producer": "identity-merchant-service",
                  "data": {
                    "userId": "%s",
                    "email": "order-%s@example.com",
                    "fullName": "Updated Newer Name",
                    "phoneNumber": "+94770000002",
                    "roles": ["CUSTOMER"],
                    "status": "ACTIVE"
                  }
                }
                """.formatted(UUID.randomUUID(), time2, userId, UUID.randomUUID());
        listener.onMessage(createJsonMessage(newerUpdate));

        Contact contactAfterNewer = contactRepository.findById(userId).orElseThrow();
        assertEquals("Updated Newer Name", contactAfterNewer.getFullName());

        // Stale update arriving late should be ignored
        String staleUpdate = """
                {
                  "eventId": "%s",
                  "eventType": "user.updated",
                  "version": 1,
                  "occurredAt": "%s",
                  "producer": "identity-merchant-service",
                  "data": {
                    "userId": "%s",
                    "email": "order-%s@example.com",
                    "fullName": "Stale Outdated Name",
                    "phoneNumber": "+94770000003",
                    "roles": ["CUSTOMER"],
                    "status": "ACTIVE"
                  }
                }
                """.formatted(UUID.randomUUID(), olderTime, userId, UUID.randomUUID());
        listener.onMessage(createJsonMessage(staleUpdate));

        Contact contactAfterStale = contactRepository.findById(userId).orElseThrow();
        assertEquals("Updated Newer Name", contactAfterStale.getFullName());
    }

    @Test
    void merchantApprovedSendsEmailWhenContactExists() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Seed contact first
        String reg = """
                {
                  "eventId": "%s",
                  "eventType": "user.registered",
                  "version": 1,
                  "occurredAt": "2026-11-01T09:30:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "userId": "%s",
                    "email": "merchant-%s@example.com",
                    "fullName": "Merchant Owner",
                    "phoneNumber": "+94771122334",
                    "roles": ["CUSTOMER"],
                    "status": "ACTIVE"
                  }
                }
                """.formatted(UUID.randomUUID(), userId, UUID.randomUUID());
        listener.onMessage(createJsonMessage(reg));

        String approved = """
                {
                  "eventId": "%s",
                  "eventType": "merchant.approved",
                  "version": 1,
                  "occurredAt": "2026-11-01T10:00:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "merchantId": "%s",
                    "userId": "%s",
                    "businessName": "Acme Deliveries",
                    "status": "APPROVED",
                    "reason": null
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), userId);

        listener.onMessage(createJsonMessage(approved));

        assertTrue(processedEventRepository.existsByEventIdAndConsumer(eventId, IdentityEventHandler.CONSUMER));

        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUserId().equals(userId) && "merchant.approved".equals(n.getTemplate()))
                .toList();
        assertEquals(1, notifications.size());
        assertEquals(NotificationStatus.SENT, notifications.getFirst().getStatus());
    }

    @Test
    void merchantApprovedWithoutContactRecordsEventAndDoesNotThrow() {
        UUID eventId = UUID.randomUUID();
        UUID randomUserId = UUID.randomUUID();

        String approved = """
                {
                  "eventId": "%s",
                  "eventType": "merchant.approved",
                  "version": 1,
                  "occurredAt": "2026-11-01T10:00:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "merchantId": "%s",
                    "userId": "%s",
                    "businessName": "Ghost Business",
                    "status": "APPROVED",
                    "reason": null
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), randomUserId);

        listener.onMessage(createJsonMessage(approved));
        assertTrue(processedEventRepository.existsByEventIdAndConsumer(eventId, IdentityEventHandler.CONSUMER));
        assertTrue(contactRepository.findById(randomUserId).isEmpty());
    }

    @Test
    void unreadableJsonThrowsAmqpRejectAndDontRequeue() {
        Message msg = createJsonMessage("hello");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.onMessage(msg));
    }

    @Test
    void missingEnvelopeFieldsThrowsAmqpRejectAndDontRequeue() {
        String missingEventId = """
                {
                  "eventType": "user.registered",
                  "occurredAt": "2026-11-01T09:30:00Z",
                  "data": { "userId": "8f3c2a10-4b7d-4e9a-a1c2-5d6e7f809a1b" }
                }
                """;
        Message msg = createJsonMessage(missingEventId);
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.onMessage(msg));
    }
}
