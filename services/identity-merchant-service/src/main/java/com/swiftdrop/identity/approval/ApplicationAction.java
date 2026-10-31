package com.swiftdrop.identity.approval;

import static com.swiftdrop.identity.approval.ApplicationStatus.APPROVED;
import static com.swiftdrop.identity.approval.ApplicationStatus.PENDING;
import static com.swiftdrop.identity.approval.ApplicationStatus.REJECTED;
import static com.swiftdrop.identity.approval.ApplicationStatus.SUSPENDED;

/**
 * Everything that can happen to a merchant or courier application, with the only status it is
 * allowed from and the status it leads to (the table in the OpenAPI contract).
 */
public enum ApplicationAction {

    SUBMIT(null, PENDING, "submitted"),
    RESUBMIT(REJECTED, PENDING, "submitted"),
    APPROVE(PENDING, APPROVED, "approved"),
    REJECT(PENDING, REJECTED, "rejected"),
    SUSPEND(APPROVED, SUSPENDED, "suspended"),
    REACTIVATE(SUSPENDED, APPROVED, "reactivated");

    private final ApplicationStatus from;
    private final ApplicationStatus to;
    private final String eventVerb;

    ApplicationAction(ApplicationStatus from, ApplicationStatus to, String eventVerb) {
        this.from = from;
        this.to = to;
        this.eventVerb = eventVerb;
    }

    public boolean allowedFrom(ApplicationStatus current) {
        return from == current;
    }

    public ApplicationStatus to() {
        return to;
    }

    /** Event type for an application kind, for example {@code merchant.approved}. */
    public String eventType(String entity) {
        return entity + "." + eventVerb;
    }

    /** True for actions a dispatcher takes (as opposed to the applicant). */
    public boolean isDecision() {
        return this != SUBMIT && this != RESUBMIT;
    }

    /** Whether the user should hold the merchant/courier role after this action. */
    public boolean grantsRole() {
        return to == APPROVED;
    }

    public boolean revokesRole() {
        return this == SUSPEND;
    }
}
