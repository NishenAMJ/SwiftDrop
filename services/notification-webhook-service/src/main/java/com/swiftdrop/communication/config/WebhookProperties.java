package com.swiftdrop.communication.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftdrop.webhook")
public class WebhookProperties {

    private String encryptionKey;
    private boolean allowHttp = true;
    private boolean allowPrivateAddresses = true;
    private Duration pollInterval = Duration.ofSeconds(5);
    private List<Duration> retryDelays = new ArrayList<>(List.of(
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5)
    ));

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public boolean isAllowHttp() {
        return allowHttp;
    }

    public void setAllowHttp(boolean allowHttp) {
        this.allowHttp = allowHttp;
    }

    public boolean isAllowPrivateAddresses() {
        return allowPrivateAddresses;
    }

    public void setAllowPrivateAddresses(boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public List<Duration> getRetryDelays() {
        return retryDelays;
    }

    public void setRetryDelays(List<Duration> retryDelays) {
        this.retryDelays = retryDelays;
    }
}
