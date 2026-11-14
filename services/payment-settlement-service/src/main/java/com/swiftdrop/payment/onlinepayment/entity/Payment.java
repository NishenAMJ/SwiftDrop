package com.swiftdrop.payment.onlinepayment.entity;

import com.swiftdrop.payment.onlinepayment.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "payment_settlement")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false, length = 100)
    private String bookingId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "customer_id")
    private UUID customerId;
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { this.customerId = value; }

    @Column(name = "merchant_id")
    private UUID merchantId;
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID value) { this.merchantId = value; }

    @Column(name = "initiated_by")
    private UUID initiatedBy;
    public UUID getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(UUID value) { this.initiatedBy = value; }

    @Column(name = "idempotency_key")
    private String idempotencyKey;
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }

    @Column(name = "pricing_snapshot_id", length = 64)
    private String pricingSnapshotId;
    public String getPricingSnapshotId() { return pricingSnapshotId; }
    public void setPricingSnapshotId(String value) { this.pricingSnapshotId = value; }

    @Column(name = "booking_channel")
    private String bookingChannel;
    public String getBookingChannel() { return bookingChannel; }
    public void setBookingChannel(String value) { this.bookingChannel = value; }

    @Column(name = "payer_json")
    private String payerJson;
    public String getPayerJson() { return payerJson; }
    public void setPayerJson(String value) { this.payerJson = value; }

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal value) { this.refundedAmount = value; }

    @Column(name = "reserved_amount", precision = 12, scale = 2)
    private BigDecimal reservedAmount = BigDecimal.ZERO;
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public void setReservedAmount(BigDecimal value) { this.reservedAmount = value; }

    @Version
    @Column(name = "version")
    private long version;
    public long getVersion() { return version; }
    public void setVersion(long value) { this.version = value; }

    @Column(name = "reconcile_after")
    private LocalDateTime reconcileAfter;
    public LocalDateTime getReconcileAfter() { return reconcileAfter; }
    public void setReconcileAfter(LocalDateTime value) { this.reconcileAfter = value; }


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
