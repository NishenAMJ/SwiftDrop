package com.swiftdrop.payment.cod.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cod_collections", schema = "payment_settlement")
public class CodCollection {
    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false, length = 100)
    private String bookingId;

    @Column(name = "tracking_number", nullable = false, length = 40)
    private String trackingNumber;

    @Column(name = "pricing_snapshot_id", nullable = false, length = 64)
    private String pricingSnapshotId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "delivery_attempt_id", nullable = false)
    private UUID deliveryAttemptId;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "collected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CodCollectionStatus status;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getPricingSnapshotId() { return pricingSnapshotId; }
    public void setPricingSnapshotId(String pricingSnapshotId) { this.pricingSnapshotId = pricingSnapshotId; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }
    public UUID getDeliveryAttemptId() { return deliveryAttemptId; }
    public void setDeliveryAttemptId(UUID deliveryAttemptId) { this.deliveryAttemptId = deliveryAttemptId; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public BigDecimal getCollectedAmount() { return collectedAmount; }
    public void setCollectedAmount(BigDecimal collectedAmount) { this.collectedAmount = collectedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }
    public CodCollectionStatus getStatus() { return status; }
    public void setStatus(CodCollectionStatus status) { this.status = status; }
    public UUID getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public long getVersion() { return version; }
}
