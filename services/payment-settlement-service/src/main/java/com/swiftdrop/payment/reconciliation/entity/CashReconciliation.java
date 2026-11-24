package com.swiftdrop.payment.reconciliation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_reconciliations", schema = "payment_settlement")
public class CashReconciliation {
    @Id
    private UUID id;

    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "received_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal receivedAmount;

    @Column(name = "difference_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal differenceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "investigation_note", length = 500)
    private String investigationNote;

    @Column(name = "confirmed_by", nullable = false)
    private UUID confirmedBy;

    @Column(name = "confirmation_idempotency_key", nullable = false, length = 128)
    private String confirmationIdempotencyKey;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID handoverId) { this.handoverId = handoverId; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public BigDecimal getReceivedAmount() { return receivedAmount; }
    public void setReceivedAmount(BigDecimal receivedAmount) { this.receivedAmount = receivedAmount; }
    public BigDecimal getDifferenceAmount() { return differenceAmount; }
    public void setDifferenceAmount(BigDecimal differenceAmount) { this.differenceAmount = differenceAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }
    public String getInvestigationNote() { return investigationNote; }
    public void setInvestigationNote(String investigationNote) { this.investigationNote = investigationNote; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(UUID confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getConfirmationIdempotencyKey() { return confirmationIdempotencyKey; }
    public void setConfirmationIdempotencyKey(String key) { this.confirmationIdempotencyKey = key; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
