package com.swiftdrop.communication.notification;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.communication.channel.NotificationChannel;
import com.swiftdrop.communication.contact.Contact;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Map<String, NotificationChannel> channelMap;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(List<NotificationChannel> channels,
                               NotificationRepository notificationRepository,
                               Clock clock) {
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(c -> c.getChannelName().toUpperCase(), c -> c));
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEmail(Contact contact, String templateName, EmailContent content, UUID parcelId) {
        send("EMAIL", contact, templateName, content.subject(), content.body(), parcelId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendSms(Contact contact, String templateName, String message, UUID parcelId) {
        send("SMS", contact, templateName, templateName, message, parcelId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void send(String channelName, Contact contact, String template, String subject, String body, UUID parcelId) {
        UUID userId = contact.getUserId();
        String normalizedChannel = channelName.toUpperCase();

        if (!"ACTIVE".equalsIgnoreCase(contact.getStatus())) {
            log.info("Skipping {} notification for inactive contact user ID: {}", normalizedChannel, userId);
            Notification failedNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    parcelId,
                    normalizedChannel,
                    template,
                    NotificationStatus.FAILED,
                    "Recipient is not active",
                    clock.instant()
            );
            notificationRepository.save(failedNotification);
            return;
        }

        NotificationChannel channel = channelMap.get(normalizedChannel);
        if (channel == null) {
            log.warn("Unsupported notification channel: {}", normalizedChannel);
            Notification failedNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    parcelId,
                    normalizedChannel,
                    template,
                    NotificationStatus.FAILED,
                    "Unsupported channel: " + normalizedChannel,
                    clock.instant()
            );
            notificationRepository.save(failedNotification);
            return;
        }

        try {
            channel.send(contact, subject, body, parcelId);

            Notification sentNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    parcelId,
                    normalizedChannel,
                    template,
                    NotificationStatus.SENT,
                    null,
                    clock.instant()
            );
            notificationRepository.save(sentNotification);
            log.info("Sent {} notification for user ID: {}, template: {}", normalizedChannel, userId, template);
        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (reason != null && reason.length() > 250) {
                reason = reason.substring(0, 250);
            }
            log.warn("Failed to send {} for user ID: {}, error: {}", normalizedChannel, userId, ex.getClass().getSimpleName());

            Notification failedNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    parcelId,
                    normalizedChannel,
                    template,
                    NotificationStatus.FAILED,
                    reason != null ? reason : "Unknown dispatch error",
                    clock.instant()
            );
            notificationRepository.save(failedNotification);
        }
    }
}
