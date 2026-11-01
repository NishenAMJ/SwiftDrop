package com.swiftdrop.identity.approval;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.springframework.http.HttpStatus;

import com.swiftdrop.identity.common.ApiException;

/**
 * Status fields shared by merchant and courier applications, and the only place their status
 * changes. Subclasses add the application details.
 */
@MappedSuperclass
public abstract class Application {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_decision_status", length = 20)
    private ApplicationStatus lastDecisionStatus;

    @Column(name = "last_decision_reason", length = 500)
    private String lastDecisionReason;

    @Column(name = "last_decided_at")
    private Instant lastDecidedAt;

    @Column(name = "last_decided_by")
    private UUID lastDecidedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Application() {
    }

    protected Application(Instant now) {
        this.status = ApplicationStatus.PENDING;
        this.submittedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applies an action (not SUBMIT, which is the constructor).
     *
     * @return the status before the change
     * @throws ApiException {@code 409 INVALID_STATUS_TRANSITION} if not allowed from the current status
     */
    public ApplicationStatus apply(ApplicationAction action, UUID actorId, String reason, Instant now) {
        if (!action.allowedFrom(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "Cannot " + action.name().toLowerCase() + " an application that is " + status + ".");
        }
        ApplicationStatus previous = status;
        status = action.to();
        updatedAt = now;
        if (action == ApplicationAction.RESUBMIT) {
            submittedAt = now;
        }
        if (action.isDecision()) {
            lastDecisionStatus = status;
            lastDecisionReason = reason;
            lastDecidedAt = now;
            lastDecidedBy = actorId;
        }
        return previous;
    }

    protected void touch(Instant now) {
        updatedAt = now;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public boolean isApproved() {
        return status == ApplicationStatus.APPROVED;
    }

    /** Null until a dispatcher has decided on the application. */
    public LastDecision lastDecision() {
        return lastDecidedAt == null ? null
                : new LastDecision(lastDecisionStatus, lastDecisionReason, lastDecidedAt, lastDecidedBy);
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
