package com.swiftdrop.communication.channel;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.swiftdrop.communication.common.PhoneMasker;
import com.swiftdrop.communication.contact.Contact;

@Component
public class MockSmsSender implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public String getChannelName() {
        return "SMS";
    }

    @Override
    public void send(Contact contact, String subject, String body, UUID parcelId) {
        String phoneNumber = contact.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Contact has no phone number");
        }

        String maskedPhone = PhoneMasker.mask(phoneNumber);
        if (parcelId != null) {
            log.info("Mock SMS sent to {} for parcel {}", maskedPhone, parcelId);
        } else {
            log.info("Mock SMS sent to {}", maskedPhone);
        }
    }
}
