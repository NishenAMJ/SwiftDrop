package com.swiftdrop.communication.webhook.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public class UpdateSubscriptionRequest {

    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String url;

    private List<String> eventTypes;

    private Boolean active;

    public UpdateSubscriptionRequest() {
    }

    public UpdateSubscriptionRequest(String url, List<String> eventTypes, Boolean active) {
        this.url = url;
        this.eventTypes = eventTypes;
        this.active = active;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
