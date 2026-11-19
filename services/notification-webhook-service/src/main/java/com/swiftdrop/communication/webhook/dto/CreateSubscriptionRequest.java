package com.swiftdrop.communication.webhook.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class CreateSubscriptionRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String url;

    @NotEmpty(message = "At least one event type must be specified")
    private List<String> eventTypes;

    public CreateSubscriptionRequest() {
    }

    public CreateSubscriptionRequest(String url, List<String> eventTypes) {
        this.url = url;
        this.eventTypes = eventTypes;
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
}
