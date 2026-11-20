package com.swiftdrop.payment.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchant_settlements", schema = "payment_settlement")
public class MerchantSettlement {
    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "cutoff_at", nullable = false)
    private LocalDateTime cutoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "calculated_by", nullable = false)
    private UUID calculatedBy;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    @Column(name = "approval_idempotency_key", length = 128)
    private String approvalIdempotencyKey;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "external_payment_reference", length = 200)
    private String externalPaymentReference;

    @Column(name = "payment_idempotency_key", length = 128)
    private String paymentIdempotencyKey;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getCutoffAt() { return cutoffAt; }
    public void setCutoffAt(LocalDateTime cutoffAt) { this.cutoffAt = cutoffAt; }
    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public UUID getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(UUID calculatedBy) { this.calculatedBy = calculatedBy; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalNote() { return approvalNote; }
    public void setApprovalNote(String approvalNote) { this.approvalNote = approvalNote; }
    public String getApprovalIdempotencyKey() { return approvalIdempotencyKey; }
    public void setApprovalIdempotencyKey(String key) { this.approvalIdempotencyKey = key; }
    public UUID getPaidBy() { return paidBy; }
    public void setPaidBy(UUID paidBy) { this.paidBy = paidBy; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public String getExternalPaymentReference() { return externalPaymentReference; }
    public void setExternalPaymentReference(String reference) { this.externalPaymentReference = reference; }
    public String getPaymentIdempotencyKey() { return paymentIdempotencyKey; }
    public void setPaymentIdempotencyKey(String key) { this.paymentIdempotencyKey = key; }
    public long getVersion() { return version; }
}
