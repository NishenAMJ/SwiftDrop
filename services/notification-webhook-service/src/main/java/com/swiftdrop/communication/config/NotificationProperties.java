package com.swiftdrop.communication.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftdrop.notification")
public class NotificationProperties {

    private String fromEmail = "noreply@swiftdrop.dev";
    private String trackingBaseUrl = "http://localhost:4200/track";
    private Parcel parcel = new Parcel();

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getTrackingBaseUrl() {
        return trackingBaseUrl;
    }

    public void setTrackingBaseUrl(String trackingBaseUrl) {
        this.trackingBaseUrl = trackingBaseUrl;
    }

    public Parcel getParcel() {
        return parcel;
    }

    public void setParcel(Parcel parcel) {
        this.parcel = parcel;
    }

    public static class Parcel {
        private List<String> emailStatuses = new ArrayList<>(List.of(
                "CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"
        ));
        private List<String> smsStatuses = new ArrayList<>(List.of(
                "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"
        ));

        public List<String> getEmailStatuses() {
            return emailStatuses;
        }

        public void setEmailStatuses(List<String> emailStatuses) {
            this.emailStatuses = emailStatuses;
        }

        public List<String> getSmsStatuses() {
            return smsStatuses;
        }

        public void setSmsStatuses(List<String> smsStatuses) {
            this.smsStatuses = smsStatuses;
        }
    }
}
