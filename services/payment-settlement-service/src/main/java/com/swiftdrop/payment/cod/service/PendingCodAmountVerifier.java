package com.swiftdrop.payment.cod.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.cod.verification-mode", havingValue = "pending", matchIfMissing = true)
final class PendingCodAmountVerifier implements CodAmountVerifier {
    PendingCodAmountVerifier(Environment environment) {
        if (environment.getProperty("payment.cod.consumer.enabled", Boolean.class, false))
            throw new IllegalStateException(
                "PAYMENT_COD_CONSUMER_ENABLED requires an approved COD amount verifier; pending mode is fail-closed.");
    }

    @Override
    public void verify(CodCollectedEvent.Data collection) {
        throw new CodVerificationUnavailable(
            "COD pricing verification is not enabled until the service-to-service contract is approved.");
    }
}
