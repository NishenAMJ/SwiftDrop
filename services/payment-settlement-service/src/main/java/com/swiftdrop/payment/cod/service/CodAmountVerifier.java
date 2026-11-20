package com.swiftdrop.payment.cod.service;

public interface CodAmountVerifier {
    void verify(CodCollectedEvent.Data collection);
}
