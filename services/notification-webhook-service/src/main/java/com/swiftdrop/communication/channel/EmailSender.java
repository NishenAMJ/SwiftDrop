package com.swiftdrop.communication.channel;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.swiftdrop.communication.config.NotificationProperties;
import com.swiftdrop.communication.contact.Contact;

@Component
public class EmailSender implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;

    public EmailSender(JavaMailSender mailSender, NotificationProperties notificationProperties) {
        this.mailSender = mailSender;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    @Override
    public void send(Contact contact, String subject, String body, UUID parcelId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(notificationProperties.getFromEmail());
        message.setTo(contact.getEmail());
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.debug("Delivered email via SMTP for user ID: {}", contact.getUserId());
    }
}
