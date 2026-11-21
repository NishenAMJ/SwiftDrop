package com.swiftdrop.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmsTemplatesTest {

    @Test
    void outForDeliveryTemplate() {
        String msg = SmsTemplates.parcelStatus("OUT_FOR_DELIVERY", "SD-123456", null);
        assertThat(msg).contains("SD-123456").contains("out for delivery today");
        assertThat(msg.length()).isLessThanOrEqualTo(160);
    }

    @Test
    void deliveredTemplate() {
        String msg = SmsTemplates.parcelStatus("DELIVERED", "SD-123456", null);
        assertThat(msg).contains("SD-123456").contains("has been delivered");
        assertThat(msg.length()).isLessThanOrEqualTo(160);
    }

    @Test
    void failedWithReasonTemplate() {
        String msg = SmsTemplates.parcelStatus("FAILED", "SD-123456", "Gate locked");
        assertThat(msg).contains("Gate locked").contains("Delivery failed");
        assertThat(msg.length()).isLessThanOrEqualTo(160);
    }

    @Test
    void failedWithoutReasonTemplate() {
        String msg = SmsTemplates.parcelStatus("FAILED", "SD-123456", null);
        assertThat(msg).contains("Delivery failed").contains("check your tracking link");
        assertThat(msg.length()).isLessThanOrEqualTo(160);
    }

    @Test
    void longMessageTruncatesTo160() {
        String longReason = "A".repeat(300);
        String msg = SmsTemplates.parcelStatus("FAILED", "SD-123456", longReason);
        assertThat(msg.length()).isLessThanOrEqualTo(160);
        assertThat(msg).endsWith("...");
    }
}
