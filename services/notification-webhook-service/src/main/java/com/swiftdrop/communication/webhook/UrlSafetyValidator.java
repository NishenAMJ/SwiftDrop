package com.swiftdrop.communication.webhook;

import java.net.InetAddress;
import java.net.URI;

import org.springframework.stereotype.Component;

import com.swiftdrop.communication.common.ApiException;
import com.swiftdrop.communication.config.WebhookProperties;

@Component
public class UrlSafetyValidator {

    private final WebhookProperties properties;

    public UrlSafetyValidator(WebhookProperties properties) {
        this.properties = properties;
    }

    public void validateUrl(String url) {
        validate(url);
    }

    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw ApiException.validation("url", "URL cannot be blank.");
        }

        if (url.length() > 2048) {
            throw ApiException.validation("url", "URL length cannot exceed 2048 characters.");
        }

        URI uri;
        try {
            uri = URI.create(url);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw ApiException.validation("url", "Malformed URL format.");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw ApiException.validation("url", "URL must include a scheme.");
        }

        if ("http".equalsIgnoreCase(scheme)) {
            if (!properties.isAllowHttp()) {
                throw ApiException.validation("url", "HTTP is not permitted; use HTTPS.");
            }
        } else if (!"https".equalsIgnoreCase(scheme)) {
            throw ApiException.validation("url", "Only HTTPS URLs are allowed.");
        }

        if (!properties.isAllowPrivateAddresses()) {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
                for (InetAddress address : addresses) {
                    if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                            || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                        throw ApiException.validation("url", "Private, loopback and link-local addresses are not allowed.");
                    }
                }
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                throw ApiException.validation("url", "Unable to resolve target host: " + uri.getHost());
            }
        }
    }
}
