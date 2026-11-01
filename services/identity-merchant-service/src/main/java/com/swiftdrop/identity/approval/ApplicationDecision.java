package com.swiftdrop.identity.approval;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** One row of the audit trail. Written once, never changed. */
@Entity
@Immutable
@Table(name = "application_decisions")
public class ApplicationDecision {

    public enum Type {
        MERCHANT,
        COURIER
    }

    @Id
    @Column(name = "decision_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 20)
    private Type applicationType;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ApplicationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ApplicationStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ApplicationDecision() {
    }

    ApplicationDecision(Type applicationType, UUID applicationId, ApplicationAction action,
            ApplicationStatus fromStatus, String reason, String note, UUID actorId, Instant decidedAt) {
        this.id = UUID.randomUUID();
        this.applicationType = applicationType;
        this.applicationId = applicationId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = action.to();
        this.reason = reason;
        this.note = note;
        this.actorId = actorId;
        this.decidedAt = decidedAt;
    }

    public ApplicationAction getAction() {
        return action;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getNote() {
        return note;
    }
}
