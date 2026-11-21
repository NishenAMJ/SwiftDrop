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

/** One line of the audit trail: an assignment moved from one status to another, and who did it. */
@Entity
@Table(name = "assignment_status_history")
public class AssignmentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private DeliveryAssignment assignment;

    @Column(name = "parcel_reference", nullable = false, updatable = false, length = 50)
    private String parcelReference;

    /** Null for the first entry, when the parcel was assigned. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 30)
    private AssignmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 30)
    private AssignmentStatus toStatus;

    /** The identity user id from the access token's sub. */
    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(name = "changed_by_role", nullable = false, updatable = false, length = 20)
    private String changedByRole;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AssignmentStatusChange() {
    }

    public AssignmentStatusChange(DeliveryAssignment assignment, AssignmentStatus fromStatus,
                                  AssignmentStatus toStatus, UUID changedBy, String changedByRole,
                                  Instant changedAt) {
        this.assignment = assignment;
        this.parcelReference = assignment.getParcelReference();
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedByRole = changedByRole;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public DeliveryAssignment getAssignment() { return assignment; }
    public String getParcelReference() { return parcelReference; }
    public AssignmentStatus getFromStatus() { return fromStatus; }
    public AssignmentStatus getToStatus() { return toStatus; }
    public UUID getChangedBy() { return changedBy; }
    public String getChangedByRole() { return changedByRole; }
    public Instant getChangedAt() { return changedAt; }
}
