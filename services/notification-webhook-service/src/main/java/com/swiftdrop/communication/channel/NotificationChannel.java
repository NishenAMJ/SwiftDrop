package com.swiftdrop.communication.channel;

import java.util.UUID;

import com.swiftdrop.communication.contact.Contact;

public interface NotificationChannel {

    String getChannelName();

    void send(Contact contact, String subject, String body, UUID parcelId);
}
