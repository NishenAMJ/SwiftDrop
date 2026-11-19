package com.swiftdrop.communication.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ContactTest {

    @Test
    void newerEventUpdatesContact() {
        UUID userId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-11-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-01T12:00:00Z");
        Instant now = Instant.parse("2026-11-01T12:05:00Z");

        Contact contact = new Contact(userId, "old@example.com", "Old Name", "+94771111111", "ACTIVE", t1, t1, t1);

        UserEventData update = new UserEventData();
        update.setUserId(userId);
        update.setEmail("new@example.com");
        update.setFullName("New Name");
        update.setPhoneNumber("+94772222222");
        update.setStatus("ACTIVE");

        boolean applied = contact.apply(update, t2, now);

        assertThat(applied).isTrue();
        assertThat(contact.getEmail()).isEqualTo("new@example.com");
        assertThat(contact.getFullName()).isEqualTo("New Name");
        assertThat(contact.getPhoneNumber()).isEqualTo("+94772222222");
        assertThat(contact.getLastEventOccurredAt()).isEqualTo(t2);
        assertThat(contact.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void olderEventDoesNotOverwriteNewerContact() {
        UUID userId = UUID.randomUUID();
        Instant newer = Instant.parse("2026-11-01T12:00:00Z");
        Instant older = Instant.parse("2026-11-01T10:00:00Z");
        Instant now = Instant.parse("2026-11-01T12:05:00Z");

        Contact contact = new Contact(userId, "current@example.com", "Current Name", "+94771111111", "ACTIVE", newer, newer, newer);

        UserEventData staleUpdate = new UserEventData();
        staleUpdate.setUserId(userId);
        staleUpdate.setEmail("stale@example.com");
        staleUpdate.setFullName("Stale Name");
        staleUpdate.setPhoneNumber("+94770000000");
        staleUpdate.setStatus("INACTIVE");

        boolean applied = contact.apply(staleUpdate, older, now);

        assertThat(applied).isFalse();
        assertThat(contact.getEmail()).isEqualTo("current@example.com");
        assertThat(contact.getFullName()).isEqualTo("Current Name");
        assertThat(contact.getPhoneNumber()).isEqualTo("+94771111111");
        assertThat(contact.getLastEventOccurredAt()).isEqualTo(newer);
    }
}
