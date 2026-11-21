package com.swiftdrop.parcel_pricing_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and updates the signed-in user's account in identity-merchant-service (Member 1).
 *
 * <p>Identity owns name, email and phone. This service only keeps a copy for receipts and payer
 * details, so it always asks Identity first. The caller's own access token is forwarded, so
 * Identity applies its normal rules and a user can only ever read or change their own account.
 */
@Component
public class IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdentityUser(UUID id, String email, String fullName, String phoneNumber) {
    }

    private final String baseUrl;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public IdentityClient(@Value("${swiftdrop.identity.base-url:http://localhost:8081}") String baseUrl, JsonMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    /** The current user, or empty when Identity cannot be reached. Never throws for outages. */
    public Optional<IdentityUser> currentUser(Jwt jwt) {
        if (jwt == null) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/users/me"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + jwt.getTokenValue())
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Identity /users/me returned status {}", response.statusCode());
                return Optional.empty();
            }
            return Optional.of(json.readValue(response.body(), IdentityUser.class));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Identity service is unavailable: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Changes name and/or phone in Identity. Null values are left unchanged. */
    public IdentityUser updateCurrentUser(Jwt jwt, String fullName, String phoneNumber) {
        Map<String, String> body = new HashMap<>();
        if (fullName != null) body.put("fullName", fullName);
        if (phoneNumber != null) body.put("phoneNumber", phoneNumber);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/users/me"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + jwt.getTokenValue())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400) {
                throw ApiException.badRequest("INVALID_PROFILE",
                        "Name or phone was rejected. Use the international phone format, for example +94771234567.");
            }
            if (response.statusCode() != 200) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                        "Your account details could not be updated right now. Please try again.");
            }
            return json.readValue(response.body(), IdentityUser.class);
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                "Your account details could not be updated right now. Please try again.");
    }

    /** Converts local Sri Lankan numbers such as 077 123 4567 to Identity's +94771234567 format. */
    public static String toE164(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[\\s()-]", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("94")) return "+" + digits;
        if (digits.startsWith("0")) return "+94" + digits.substring(1);
        return "+94" + digits;
    }
}
