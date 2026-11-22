package com.swiftdrop.payment.cod.service;

public interface CodAmountVerifier {
    void verify(CodCollectedEvent.Data collection);
    default java.util.UUID verifyCustomer(CodCollectedEvent.Data collection) {
        throw new CodVerificationUnavailable("Customer COD verification is not configured.");
    }
}
