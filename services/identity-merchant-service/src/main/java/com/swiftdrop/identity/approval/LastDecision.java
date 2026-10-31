package com.swiftdrop.identity.approval;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The {@code StatusDecision} schema: the latest dispatcher decision on an application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LastDecision(ApplicationStatus status, String reason, Instant decidedAt, UUID decidedBy) {

    /** The applicant sees the decision but not which dispatcher made it. */
    public LastDecision withoutDecider() {
        return new LastDecision(status, reason, decidedAt, null);
    }
}
