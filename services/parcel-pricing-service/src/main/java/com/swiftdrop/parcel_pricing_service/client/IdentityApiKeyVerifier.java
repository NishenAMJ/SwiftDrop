package com.swiftdrop.parcel_pricing_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import com.swiftdrop.parcel_pricing_service.security.MerchantApiKeyVerifier;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Verifies a merchant API key with identity-merchant-service: the key is exchanged at
 * {@code POST /api/v1/auth/token} for a short-lived MERCHANT access token, which is then checked
 * with the same JWT decoder as every other request. The key is never stored or logged.
 *
 * <p>401 (unknown, revoked or expired key) and 403 (merchant suspended) mean "not a valid key";
 * anything else means Identity could not decide, and the request is refused with 503.
 */
@Component
public class IdentityApiKeyVerifier implements MerchantApiKeyVerifier {

    private static final Logger log = LoggerFactory.getLogger(IdentityApiKeyVerifier.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken) {
    }

    private final String baseUrl;
    private final JsonMapper json;
    private final JwtDecoder jwtDecoder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public IdentityApiKeyVerifier(@Value("${swiftdrop.identity.base-url:http://localhost:8081}") String baseUrl,
                                  JsonMapper json,
                                  JwtDecoder jwtDecoder) {
        this.baseUrl = baseUrl;
        this.json = json;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Optional<MerchantPrincipal> verify(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/token"))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-API-Key", apiKey)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiKeyVerificationUnavailableException(ex);
        } catch (Exception ex) {
            log.warn("Identity API key exchange failed: {}", ex.getClass().getSimpleName());
            throw new ApiKeyVerificationUnavailableException(ex);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) return Optional.empty();
        if (status != 200) {
            log.warn("Identity API key exchange returned status {}", status);
            throw new ApiKeyVerificationUnavailableException();
        }

        try {
            String token = json.readValue(response.body(), TokenResponse.class).accessToken();
            if (token == null || token.isBlank()) throw new ApiKeyVerificationUnavailableException();
            return Optional.of(MerchantPrincipal.fromJwt(jwtDecoder.decode(token)));
        } catch (JwtException | UnauthenticatedMerchantException ex) {
            // Identity answered 200 but the token is not a usable merchant token: a configuration fault.
            log.error("Identity returned an unusable token for an API key exchange: {}", ex.getClass().getSimpleName());
            throw new ApiKeyVerificationUnavailableException(ex);
        } catch (ApiKeyVerificationUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Identity API key exchange answer could not be read: {}", ex.getClass().getSimpleName());
            throw new ApiKeyVerificationUnavailableException(ex);
        }
    }
}
