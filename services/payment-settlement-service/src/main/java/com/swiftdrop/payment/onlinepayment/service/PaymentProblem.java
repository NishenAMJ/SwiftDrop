package com.swiftdrop.payment.onlinepayment.service;

public class PaymentProblem extends RuntimeException {
    public final int status;
    public final String code;
    public PaymentProblem(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static PaymentProblem conflict(String message) { return new PaymentProblem(409, "CONFLICT", message); }
    public static PaymentProblem forbidden() { return new PaymentProblem(403, "FORBIDDEN", "This payment operation is not permitted."); }
    public static PaymentProblem unavailable() { return new PaymentProblem(502, "UPSTREAM_UNAVAILABLE", "The payment dependency is unavailable."); }
}
