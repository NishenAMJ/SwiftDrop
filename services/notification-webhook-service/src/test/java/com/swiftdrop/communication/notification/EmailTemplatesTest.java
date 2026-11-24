package com.swiftdrop.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTemplatesTest {

    private static final String BASE_URL = "http://localhost:4200/track";

    @Test
    void welcomeTemplateContainsName() {
        EmailContent email = EmailTemplates.welcome("Alice");
        assertThat(email.subject()).isEqualTo("Welcome to SwiftDrop");
        assertThat(email.body()).contains("Alice").contains("Welcome to SwiftDrop!");
    }

    @Test
    void merchantDecisionTemplates() {
        EmailContent approved = EmailTemplates.merchantDecision("merchant.approved", "Bakery", null);
        assertThat(approved.subject()).contains("Approved");
        assertThat(approved.body()).contains("Bakery");

        EmailContent rejected = EmailTemplates.merchantDecision("merchant.rejected", "Bakery", "Invalid doc");
        assertThat(rejected.subject()).contains("Update");
        assertThat(rejected.body()).contains("Invalid doc");

        EmailContent suspended = EmailTemplates.merchantDecision("merchant.suspended", "Bakery", "Late deliveries");
        assertThat(suspended.subject()).contains("Suspended");
        assertThat(suspended.body()).contains("Late deliveries");

        EmailContent reactivated = EmailTemplates.merchantDecision("merchant.reactivated", "Bakery", null);
        assertThat(reactivated.subject()).contains("Reactivated");
        assertThat(reactivated.body()).contains("Bakery");

        EmailContent other = EmailTemplates.merchantDecision("merchant.unknown", "Bakery", null);
        assertThat(other.subject()).contains("Notification");
    }

    @Test
    void parcelStatusTemplates() {
        String tracking = "SD-100";

        EmailContent created = EmailTemplates.parcelStatus("CREATED", tracking, BASE_URL, null);
        assertThat(created.subject()).contains("Confirmed");
        assertThat(created.body()).contains(BASE_URL + "/SD-100");

        EmailContent pickedUp = EmailTemplates.parcelStatus("PICKED_UP", tracking, BASE_URL, null);
        assertThat(pickedUp.subject()).contains("Picked Up");

        EmailContent inTransit = EmailTemplates.parcelStatus("IN_TRANSIT", tracking, BASE_URL, null);
        assertThat(inTransit.subject()).contains("In Transit");

        EmailContent outForDelivery = EmailTemplates.parcelStatus("OUT_FOR_DELIVERY", tracking, BASE_URL, null);
        assertThat(outForDelivery.subject()).contains("Out for Delivery");

        EmailContent delivered = EmailTemplates.parcelStatus("DELIVERED", tracking, BASE_URL, null);
        assertThat(delivered.subject()).contains("Delivered");

        EmailContent failedWithReason = EmailTemplates.parcelStatus("FAILED", tracking, BASE_URL, "Customer not home");
        assertThat(failedWithReason.subject()).contains("Failed");
        assertThat(failedWithReason.body()).contains("Customer not home");

        EmailContent failedWithoutReason = EmailTemplates.parcelStatus("FAILED", tracking, BASE_URL, null);
        assertThat(failedWithoutReason.subject()).contains("Failed");

        EmailContent custom = EmailTemplates.parcelStatus("CUSTOM_STATUS", tracking, BASE_URL, null);
        assertThat(custom.subject()).contains("Update");
    }
}
