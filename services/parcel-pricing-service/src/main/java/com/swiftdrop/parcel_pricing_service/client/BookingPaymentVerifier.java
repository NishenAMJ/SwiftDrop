package com.swiftdrop.parcel_pricing_service.client;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
@Component
public class BookingPaymentVerifier {
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record VerifiedPayment(String bookingId,BigDecimal amount,String currency,String status,String providerReference) {}
    private final String baseUrl;
    private final JsonMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public BookingPaymentVerifier(@Value("${swiftdrop.payment.base-url:http://localhost:8085}") String baseUrl,JsonMapper json) { this.baseUrl=baseUrl; this.json=json; }
    // Called by the controller before opening the booking finalization transaction.
    public VerifiedPayment verify(UUID paymentId,Jwt jwt) {
        if(jwt==null) throw ApiException.unauthorized("Login is required.");
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create(baseUrl+"/api/v1/payments/"+paymentId))
                .timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+jwt.getTokenValue()).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_NOT_VERIFIED","Payment could not be verified. Check its status before retrying.");
            return json.readValue(response.body(),VerifiedPayment.class);
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_UNAVAILABLE","Payment verification is unavailable."); }
        catch(ApiException ex) { throw ex; }
        catch(Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_UNAVAILABLE","Payment verification is unavailable."); }
    }
}
