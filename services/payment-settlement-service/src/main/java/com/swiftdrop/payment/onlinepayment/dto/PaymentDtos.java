package com.swiftdrop.payment.onlinepayment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.swiftdrop.payment.onlinepayment.enums.PaymentStatus;

public final class PaymentDtos {
    private PaymentDtos() {}
    public record Create(@NotBlank @Size(max=100) String bookingId) {}
    public record RefundRequest(@NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal amount,
                                @NotBlank @Size(max=500) String reason) {}
    public record RefundRetryRequest(@NotNull @AssertTrue Boolean confirmNoRefundAtProvider) {}
    public record RefundResolutionRequest(
        @NotBlank @Pattern(regexp="CONFIRMED_SUCCESS|CONFIRMED_FAILURE") String resolution,
        @NotBlank @Pattern(regexp="PAYHERE_DASHBOARD|PAYHERE_SUPPORT_CASE") String evidenceType,
        @NotBlank @Size(max=200) String evidenceReference,
        @Size(max=30) String providerRefundReference,
        @NotBlank @Size(max=500) String reason,
        @NotNull @AssertTrue Boolean confirmProviderEvidence
    ) {}
    public record BookingClaimReleaseRequest(
        @NotNull @AssertTrue Boolean confirmProviderTerminalFailure,
        @NotBlank @Pattern(regexp="PAYHERE_DASHBOARD|PAYHERE_SUPPORT_CASE") String evidenceType,
        @NotBlank @Size(max=200) String evidenceReference,
        @NotBlank @Size(max=500) String reason
    ) {}
    public record RecoveryAudit(UUID id, UUID paymentId, UUID refundId, String action,
        String priorStatus, String resultingStatus, UUID actorId, String evidenceType,
        String evidenceReference, String providerReference, String reason, Instant createdAt) {}
    public record Checkout(String provider, String checkoutUrl, String method, Map<String,String> fields) {}
    public record View(UUID id, String bookingId, BigDecimal amount, String currency, PaymentStatus status,
                       BigDecimal refundedAmount, String provider, String providerReference,
                       Instant createdAt, Instant updatedAt) {}
    public record Refund(UUID id, UUID paymentId, BigDecimal amount, String currency, String status,
                         Instant createdAt, Instant updatedAt) {}
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record Payer(String firstName, String lastName, String email, String phone,
                        String address, String city, String country) {}
    public record Context(String bookingId, String channel, UUID customerId, UUID merchantId,
                          boolean payable, UUID pricingSnapshotId, BigDecimal amount, String currency, Payer payer) {}
}
