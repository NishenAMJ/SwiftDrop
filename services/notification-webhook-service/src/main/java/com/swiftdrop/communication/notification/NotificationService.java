package com.swiftdrop.communication.notification;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.communication.config.NotificationProperties;
import com.swiftdrop.communication.contact.Contact;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    public NotificationService(JavaMailSender mailSender,
                               NotificationRepository notificationRepository,
                               NotificationProperties notificationProperties,
                               Clock clock) {
        this.mailSender = mailSender;
        this.notificationRepository = notificationRepository;
        this.notificationProperties = notificationProperties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEmail(Contact contact, String templateName, EmailContent content) {
        UUID userId = contact.getUserId();

        if (!"ACTIVE".equalsIgnoreCase(contact.getStatus())) {
            log.info("Skipping email for inactive contact user ID: {}", userId);
            Notification failedNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    "EMAIL",
                    templateName,
                    NotificationStatus.FAILED,
                    "Recipient is not active",
                    clock.instant()
            );
            notificationRepository.save(failedNotification);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(notificationProperties.getFromEmail());
            message.setTo(contact.getEmail());
            message.setSubject(content.subject());
            message.setText(content.body());

            mailSender.send(message);

            Notification sentNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    "EMAIL",
                    templateName,
                    NotificationStatus.SENT,
                    null,
                    clock.instant()
            );
            notificationRepository.save(sentNotification);
            log.info("Sent email notification for user ID: {}, template: {}", userId, templateName);
        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (reason != null && reason.length() > 250) {
                reason = reason.substring(0, 250);
            }
            log.warn("Failed to send email for user ID: {}, error: {}", userId, ex.getClass().getSimpleName());

            Notification failedNotification = new Notification(
                    UUID.randomUUID(),
                    userId,
                    "EMAIL",
                    templateName,
                    NotificationStatus.FAILED,
                    reason != null ? reason : "Unknown mail error",
                    clock.instant()
            );
            notificationRepository.save(failedNotification);
        }
    }
}
