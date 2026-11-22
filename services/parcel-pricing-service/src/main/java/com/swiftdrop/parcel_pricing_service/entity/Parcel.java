package com.swiftdrop.parcel_pricing_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parcels", schema = "parcel_pricing")
public class Parcel {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tracking_number", unique = true, length = 32)
    private String trackingNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "urgency", nullable = false, length = 16)
    private String urgency = "NORMAL";

    @Column(name = "status", nullable = false, length = 30)
    private String status = "BOOKED";

    @Column(name = "estimated_fee", precision = 10, scale = 2)
    private BigDecimal estimatedFee;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel = "DIRECT_WEBSITE";

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "ONLINE";

    /** Merchant orders only: the merchant's own order number, unique per merchant. */
    @Column(name = "merchant_reference", length = 100)
    private String merchantReference;

    /** Merchant COD orders only: cash the courier collects from the receiver for the goods. */
    @Column(name = "cod_amount", precision = 12, scale = 2)
    private BigDecimal codAmount;

    @Column(name = "urgent_note", length = 255)
    private String urgentNote;

    /** Merchant orders only: DASHBOARD or EXTERNAL_API (see {@link OrderSource}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_source", length = 20)
    private OrderSource orderSource;

    @Column(name = "pricing_snapshot_id", length = 64)
    private String pricingSnapshotId;

    @Column(name = "pricing_expires_at")
    private OffsetDateTime pricingExpiresAt;

    @Column(name = "cancellation_fee")
    private BigDecimal cancellationFee;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "cancellation_status")
    private String cancellationStatus;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_transaction_ref")
    private String cancellationTransactionRef;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "base_fee", precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "weight_charge", precision = 10, scale = 2)
    private BigDecimal weightCharge;

    @Column(name = "zone_charge", precision = 10, scale = 2)
    private BigDecimal zoneCharge;

    @Column(name = "urgent_surcharge", precision = 10, scale = 2)
    private BigDecimal urgentSurcharge;

    @Column(name = "pricing_rule_code", length = 50)
    private String pricingRuleCode;

    @Column(name = "pricing_zone_code", length = 50)
    private String pricingZoneCode;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "customer_phone", length = 25)
    private String customerPhone;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "status_updated_at")
    private OffsetDateTime statusUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ParcelAddress> addresses = new ArrayList<>();

    public Parcel() {
        this.id = UUID.randomUUID();
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if ("CANCELLED".equals(this.status) && !"CANCELLED".equals(status)) throw new IllegalStateException("Cancelled bookings cannot be reactivated");
        this.status = status;
    }

    public BigDecimal getEstimatedFee() {
        return estimatedFee;
    }

    public void setEstimatedFee(BigDecimal estimatedFee) {
        this.estimatedFee = estimatedFee;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPricingSnapshotId() {
        return pricingSnapshotId;
    }

    public void setPricingSnapshotId(String pricingSnapshotId) {
        this.pricingSnapshotId = pricingSnapshotId;
    }

    public OffsetDateTime getPricingExpiresAt() {
        return pricingExpiresAt;
    }

    public void setPricingExpiresAt(OffsetDateTime pricingExpiresAt) {
        this.pricingExpiresAt = pricingExpiresAt;
    }

    public List<ParcelAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<ParcelAddress> addresses) {
        this.addresses = addresses;
    }

    public void addAddress(ParcelAddress address) {
        addresses.add(address);
        address.setParcel(this);
    }

    public void removeAddress(ParcelAddress address) {
        addresses.remove(address);
        address.setParcel(null);
    }

    // Cancellation related getters and setters
    public BigDecimal getCancellationFee() { return cancellationFee; }
    public void setCancellationFee(BigDecimal cancellationFee) { this.cancellationFee = cancellationFee; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getCancellationStatus() { return cancellationStatus; }
    public void setCancellationStatus(String cancellationStatus) { this.cancellationStatus = cancellationStatus; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationTransactionRef() { return cancellationTransactionRef; }
    public void setCancellationTransactionRef(String cancellationTransactionRef) { this.cancellationTransactionRef = cancellationTransactionRef; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    // Pricing breakdown captured from PricingService at booking time
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public BigDecimal getWeightCharge() { return weightCharge; }
    public void setWeightCharge(BigDecimal weightCharge) { this.weightCharge = weightCharge; }
    public BigDecimal getZoneCharge() { return zoneCharge; }
    public void setZoneCharge(BigDecimal zoneCharge) { this.zoneCharge = zoneCharge; }
    public BigDecimal getUrgentSurcharge() { return urgentSurcharge; }
    public void setUrgentSurcharge(BigDecimal urgentSurcharge) { this.urgentSurcharge = urgentSurcharge; }
    public String getPricingRuleCode() { return pricingRuleCode; }
    public void setPricingRuleCode(String pricingRuleCode) { this.pricingRuleCode = pricingRuleCode; }
    public String getPricingZoneCode() { return pricingZoneCode; }
    public void setPricingZoneCode(String pricingZoneCode) { this.pricingZoneCode = pricingZoneCode; }

    // Merchant order fields
    public String getMerchantReference() { return merchantReference; }
    public void setMerchantReference(String merchantReference) { this.merchantReference = merchantReference; }
    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal codAmount) { this.codAmount = codAmount; }
    public String getUrgentNote() { return urgentNote; }
    public void setUrgentNote(String urgentNote) { this.urgentNote = urgentNote; }
    public OrderSource getOrderSource() { return orderSource; }
    public void setOrderSource(OrderSource orderSource) { this.orderSource = orderSource; }

    // Customer details from Identity at booking time
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    // Payment link and lifecycle
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }
    public OffsetDateTime getStatusUpdatedAt() { return statusUpdatedAt; }
    public void setStatusUpdatedAt(OffsetDateTime statusUpdatedAt) { this.statusUpdatedAt = statusUpdatedAt; }

    public ParcelAddress getPickupAddress() { return findAddress("PICKUP"); }
    public ParcelAddress getDeliveryAddress() { return findAddress("DELIVERY"); }

    private ParcelAddress findAddress(String type) {
        if (addresses == null) return null;
        for (ParcelAddress address : addresses) {
            if (type.equalsIgnoreCase(address.getAddressType())) return address;
        }
        return null;
    }
}

