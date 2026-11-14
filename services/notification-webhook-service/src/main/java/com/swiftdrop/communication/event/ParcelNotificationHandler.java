package com.swiftdrop.communication.event;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.communication.config.NotificationProperties;
import com.swiftdrop.communication.contact.Contact;
import com.swiftdrop.communication.contact.ContactService;
import com.swiftdrop.communication.notification.EmailContent;
import com.swiftdrop.communication.notification.EmailTemplates;
import com.swiftdrop.communication.notification.NotificationService;
import com.swiftdrop.communication.notification.SmsTemplates;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ParcelNotificationHandler {

    public static final String CONSUMER = "parcel-notifications";
    private static final Logger log = LoggerFactory.getLogger(ParcelNotificationHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ContactService contactService;
    private final NotificationService notificationService;
    private final NotificationProperties notificationProperties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ParcelNotificationHandler(ProcessedEventRepository processedEventRepository,
                                     ContactService contactService,
                                     NotificationService notificationService,
                                     NotificationProperties notificationProperties,
                                     JsonMapper jsonMapper,
                                     Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.contactService = contactService;
        this.notificationService = notificationService;
        this.notificationProperties = notificationProperties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        UUID eventId = envelope.getEventId();
        String eventType = envelope.getEventType();

        if (processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER)) {
            log.info("Skipping duplicate event: {}", eventId);
            return;
        }

        ParcelStatusEventData data = jsonMapper.convertValue(envelope.getData(), ParcelStatusEventData.class);
        UUID customerUserId = data.getCustomerUserId();
        UUID parcelId = data.getParcelId();
        String status = data.getStatus();
        String trackingNumber = data.getTrackingNumber();
        String failureReason = data.getFailureReason();

        if (customerUserId == null) {
            log.info("Skipping notification: no customerUserId present for parcelId: {}", parcelId);
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER, eventType, clock.instant()));
            return;
        }

        Optional<Contact> contactOpt = contactService.findContact(customerUserId);
        if (contactOpt.isEmpty()) {
            log.warn("No contact found for customerUserId: {}, skipping notifications for parcelId: {}",
                    customerUserId, parcelId);
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER, eventType, clock.instant()));
            return;
        }

        Contact contact = contactOpt.get();
        String templateName = "parcel." + (status != null ? status.toLowerCase().replace('_', '-') : "status-changed");

        // Email dispatch
        if (status != null && isEmailEnabledFor(status)) {
            EmailContent content = EmailTemplates.parcelStatus(
                    status,
                    trackingNumber,
                    notificationProperties.getTrackingBaseUrl(),
                    failureReason
            );
            notificationService.sendEmail(contact, templateName, content, parcelId);
        }

        // SMS dispatch
        if (status != null && isSmsEnabledFor(status)) {
            String phoneNumber = contact.getPhoneNumber();
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                String smsMessage = SmsTemplates.parcelStatus(status, trackingNumber, failureReason);
                notificationService.sendSms(contact, templateName, smsMessage, parcelId);
            }
        }

        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER, eventType, clock.instant()));
    }

    private boolean isEmailEnabledFor(String status) {
        return notificationProperties.getParcel().getEmailStatuses().stream()
                .anyMatch(s -> s.equalsIgnoreCase(status));
    }

    private boolean isSmsEnabledFor(String status) {
        return notificationProperties.getParcel().getSmsStatuses().stream()
                .anyMatch(s -> s.equalsIgnoreCase(status));
    }
}
