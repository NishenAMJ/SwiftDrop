package com.swiftdrop.delivery.courier.domain;

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

    public void moveTo(AssignmentStatus next, Instant at) {
        if (!status.canMoveTo(next)) {
            throw new InvalidOperationException("Cannot change assignment status from " + status + " to " + next);
        }
        status = next;
        switch (next) {
            case PICKED_UP -> pickedUpAt = at;
            case OUT_FOR_DELIVERY -> outForDeliveryAt = at;
            case DELIVERED, DELIVERY_FAILED -> completedAt = at;
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
