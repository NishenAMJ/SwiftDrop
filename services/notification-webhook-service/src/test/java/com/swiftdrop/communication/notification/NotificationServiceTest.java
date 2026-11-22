package com.swiftdrop.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.communication.channel.NotificationChannel;
import com.swiftdrop.communication.contact.Contact;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Every send is recorded as SENT or FAILED with a bounded, safe reason; failures never escape. */
class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationChannel email = mock(NotificationChannel.class);
    private final NotificationChannel sms = mock(NotificationChannel.class);
    private NotificationService service;
    private final UUID parcelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(email.getChannelName()).thenReturn("email");
        when(sms.getChannelName()).thenReturn("SMS");
        service = new NotificationService(List.of(email, sms), repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Contact contact(String status) {
        return new Contact(UUID.randomUUID(), "nimal@example.com", "Nimal", "+94771234567", status, NOW, NOW, NOW);
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void successfulEmailIsRecordedAsSent() {
        service.sendEmail(contact("ACTIVE"), "parcel-booked", new EmailContent("Booked", "Body"), parcelId);
        verify(email).send(any(), any(), any(), any());
        Notification notification = saved();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getChannel()).isEqualTo("EMAIL");
        assertThat(notification.getFailureReason()).isNull();
        assertThat(notification.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void inactiveRecipientIsNeverContacted() {
        service.sendSms(contact("SUSPENDED"), "out-for-delivery", "On the way", parcelId);
        verify(sms, never()).send(any(), any(), any(), any());
        assertThat(saved().getFailureReason()).isEqualTo("Recipient is not active");
    }

    @Test
    void unsupportedChannelIsRecordedAsFailed() {
        service.send("whatsapp", contact("ACTIVE"), "t", "s", "b", parcelId);
        Notification notification = saved();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("Unsupported channel: WHATSAPP");
    }

    @Test
    void providerFailureIsRecordedWithATruncatedReason() {
        doThrow(new IllegalStateException("x".repeat(400))).when(sms).send(any(), any(), any(), any());
        service.sendSms(contact("ACTIVE"), "delivered", "Delivered", parcelId);
        Notification notification = saved();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).hasSize(250);
    }

    @Test
    void providerFailureWithoutAMessageGetsAGenericReason() {
        doThrow(new RuntimeException()).when(email).send(any(), any(), any(), any());
        service.sendEmail(contact("active"), "delivered", new EmailContent("s", "b"), parcelId);
        assertThat(saved().getFailureReason()).isEqualTo("Unknown dispatch error");
    }
}
