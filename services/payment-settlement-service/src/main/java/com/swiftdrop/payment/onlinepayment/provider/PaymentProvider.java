package com.swiftdrop.payment.onlinepayment.provider;

import java.math.BigDecimal;
import java.util.*;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.*;
import com.swiftdrop.payment.onlinepayment.entity.Payment;

public interface PaymentProvider {
    Checkout checkout(Payment payment, Payer payer);
    VerifiedCallback verify(Map<String,String> fields);
    RefundResult refund(String reference, BigDecimal amount, String reason);
    List<RemotePayment> retrieve(UUID orderId);
    record VerifiedCallback(UUID orderId, String reference, BigDecimal amount, String currency, int status, String fingerprint) {}
    // SUCCESS means the authenticated Refund API confirmed the operation, not bank settlement.
    record RefundResult(String outcome, String reference) {}
    record RemotePayment(String reference, String orderId, BigDecimal amount, String currency, String status) {}
}
