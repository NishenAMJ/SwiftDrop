package com.swiftdrop.delivery.courier.domain;

import com.swiftdrop.delivery.courier.parcel.ParcelContact;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_assignments")
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Parcel & Pricing identifier, resolved from the tracking number when the parcel is assigned. */
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    /** The customer who booked the parcel; null when the booking has no registered customer. */
    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    /** Merchant orders only: the merchant the parcel belongs to; null for customer bookings. */
    @Column(name = "merchant_id", updatable = false)
    private UUID merchantId;

    /** Merchant COD orders only: the cash due at the door and the pricing snapshot it was agreed under. */

    @Column(name = "cod_amount", updatable = false, precision = 12, scale = 2)
    private BigDecimal codAmount;

    @Column(name = "pricing_snapshot_id", updatable = false, length = 64)
    private String pricingSnapshotId;

    @Column(updatable = false, length = 3)
    private String currency;

    /** Copied from Parcel & Pricing when assigned, so the courier can find and call both ends. */
    @Column(name = "pickup_contact_name", updatable = false, length = 100)
    private String pickupContactName;

    @Column(name = "pickup_contact_phone", updatable = false, length = 25)
    private String pickupContactPhone;

    @Column(name = "pickup_address", updatable = false, length = 400)
    private String pickupAddress;

    @Column(name = "delivery_contact_name", updatable = false, length = 100)
    private String deliveryContactName;

    @Column(name = "delivery_contact_phone", updatable = false, length = 25)
    private String deliveryContactPhone;

    @Column(name = "delivery_address", updatable = false, length = 400)
    private String deliveryAddress;

    @Column(name = "parcel_reference", nullable = false, length = 50)
    private String parcelReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private Courier courier;

    @Column(name = "assigned_by", nullable = false, length = 120)
    private String assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status;

    @Column(length = 500)
    private String notes;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "out_for_delivery_at")
    private Instant outForDeliveryAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryAssignment() {
    }

    public DeliveryAssignment(UUID parcelId, UUID customerId, String parcelReference, Courier courier,
                              String assignedBy, String notes, Instant assignedAt) {
        this.parcelId = parcelId;
        this.customerId = customerId;
        this.parcelReference = parcelReference;
        this.courier = courier;
        this.assignedBy = assignedBy;
        this.notes = notes;
        this.assignedAt = assignedAt;
        this.status = AssignmentStatus.ASSIGNED;
    }

    public void recordContacts(ParcelContact pickup, ParcelContact delivery) {
        if (pickup != null) {
            this.pickupContactName = pickup.name();
            this.pickupContactPhone = pickup.phone();
            this.pickupAddress = pickup.address();
        }
        if (delivery != null) {
            this.deliveryContactName = delivery.name();
            this.deliveryContactPhone = delivery.phone();
            this.deliveryAddress = delivery.address();
        }
    }

    public ParcelContact pickupContact() {
        return pickupAddress == null ? null : new ParcelContact(pickupContactName, pickupContactPhone, pickupAddress);
    }

    public ParcelContact deliveryContact() {
        return deliveryAddress == null ? null
                : new ParcelContact(deliveryContactName, deliveryContactPhone, deliveryAddress);
    }

    /** The parcel is a merchant's order; merchant webhooks and cash reporting are keyed on this. */
    public void forMerchant(UUID merchantId) {
        this.merchantId = merchantId;
    }

    /** Marks this as a Cash on Delivery parcel: the courier must collect {@code amount} at the door. */
    public void collectCash(BigDecimal amount, String pricingSnapshotId, String currency) {
        this.codAmount = amount;
        this.pricingSnapshotId = pricingSnapshotId;
        this.currency = currency == null || currency.isBlank() ? "LKR" : currency;
    }

    public boolean isCashOnDelivery() {
        return codAmount != null && merchantId != null;
    }

    public void moveTo(AssignmentStatus next, Instant at) {
        if (!status.canMoveTo(next)) {
            throw new InvalidOperationException("Cannot change assignment status from " + status + " to " + next);
        }
        status = next;
        switch (next) {
            case PICKED_UP -> pickedUpAt = at;
            case OUT_FOR_DELIVERY -> outForDeliveryAt = at;
            case DELIVERED, DELIVERY_FAILED, CANCELLED -> completedAt = at;
            default -> { }
        }
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getParcelId() { return parcelId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getMerchantId() { return merchantId; }
    public BigDecimal getCodAmount() { return codAmount; }
    public String getPricingSnapshotId() { return pricingSnapshotId; }
    public String getCurrency() { return currency; }
    public String getParcelReference() { return parcelReference; }
    public Courier getCourier() { return courier; }
    public String getAssignedBy() { return assignedBy; }
    public AssignmentStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public Instant getOutForDeliveryAt() { return outForDeliveryAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
