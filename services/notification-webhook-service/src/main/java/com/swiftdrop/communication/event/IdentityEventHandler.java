package com.swiftdrop.communication.event;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.communication.contact.Contact;
import com.swiftdrop.communication.contact.ContactService;
import com.swiftdrop.communication.contact.UserEventData;
import com.swiftdrop.communication.notification.EmailContent;
import com.swiftdrop.communication.notification.EmailTemplates;
import com.swiftdrop.communication.notification.NotificationService;

import tools.jackson.databind.json.JsonMapper;

@Component
public class IdentityEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IdentityEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ContactService contactService;
    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public IdentityEventHandler(ProcessedEventRepository processedEventRepository,
                                ContactService contactService,
                                NotificationService notificationService,
                                JsonMapper jsonMapper,
                                Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.contactService = contactService;
        this.notificationService = notificationService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        UUID eventId = envelope.getEventId();
        String eventType = envelope.getEventType();

        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate event: {}", eventId);
            return;
        }

        switch (eventType) {
            case "user.registered" -> {
                UserEventData userData = jsonMapper.convertValue(envelope.getData(), UserEventData.class);
                Contact contact = contactService.syncContact(userData, envelope.getOccurredAt());
                EmailContent content = EmailTemplates.welcome(contact.getFullName());
                notificationService.sendEmail(contact, "WELCOME", content);
            }
            case "user.updated" -> {
                UserEventData userData = jsonMapper.convertValue(envelope.getData(), UserEventData.class);
                contactService.syncContact(userData, envelope.getOccurredAt());
            }
            case "merchant.approved", "merchant.rejected", "merchant.suspended" -> {
                MerchantEventData merchantData = jsonMapper.convertValue(envelope.getData(), MerchantEventData.class);
                UUID userId = merchantData.getUserId();
                Optional<Contact> contactOpt = contactService.findContact(userId);
                if (contactOpt.isEmpty()) {
                    log.warn("No contact found for userId: {}, skipping email for merchantId: {}",
                            userId, merchantData.getMerchantId());
                } else {
                    Contact contact = contactOpt.get();
                    EmailContent content = EmailTemplates.merchantDecision(
                            eventType,
                            merchantData.getBusinessName(),
                            merchantData.getReason()
                    );
                    notificationService.sendEmail(contact, eventType, content);
                }
            }
            default -> {
                // Ignore unknown event types
                log.debug("Ignoring unhandled event type: {}", eventType);
            }
        }

        processedEventRepository.save(new ProcessedEvent(eventId, eventType, clock.instant()));
    }
}
