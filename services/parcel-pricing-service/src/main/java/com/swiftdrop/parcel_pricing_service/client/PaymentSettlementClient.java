package com.swiftdrop.parcel_pricing_service.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Stub client for the external payment settlement service.
 * In a real deployment this would be a Feign/WebClient client that talks to the payment gateway.
 */
@Service
public class PaymentSettlementClient {

    /**
     * Initiates a payment for the given booking.
     * This method should throw an exception if the payment fails.
     */
    public void initiatePayment(UUID bookingId, BigDecimal amount) {
        throw new UnsupportedOperationException("Cancellation payments are not integrated; no financial operation was performed.");
    }

    /**
     * Deducts a cancellation fee from the customer's payment method.
     */
    public void deductFee(UUID bookingId, BigDecimal fee) {
        throw new UnsupportedOperationException("Cancellation payments are not integrated; no financial operation was performed.");
    }
}
