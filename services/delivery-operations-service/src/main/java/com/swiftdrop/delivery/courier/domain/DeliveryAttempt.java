package com.swiftdrop.delivery.courier.domain;

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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private DeliveryAssignment assignment;

    @Column(name = "parcel_reference", nullable = false, length = 50)
    private String parcelReference;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FailureReason reason;

    @Column(length = 500)
    private String notes;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(DeliveryAssignment assignment, int attemptNumber, FailureReason reason, String notes,
                           Instant attemptedAt) {
        this.assignment = assignment;
        this.parcelReference = assignment.getParcelReference();
        this.attemptNumber = attemptNumber;
        this.reason = reason;
        this.notes = notes;
        this.attemptedAt = attemptedAt;
    }

    public UUID getId() { return id; }
    public DeliveryAssignment getAssignment() { return assignment; }
    public String getParcelReference() { return parcelReference; }
    public int getAttemptNumber() { return attemptNumber; }
    public FailureReason getReason() { return reason; }
    public String getNotes() { return notes; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
