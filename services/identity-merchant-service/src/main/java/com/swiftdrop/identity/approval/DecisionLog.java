package com.swiftdrop.identity.approval;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit trail, in the same transaction as the status change. */
@Component
public class DecisionLog {

    private final ApplicationDecisionRepository repository;

    public DecisionLog(ApplicationDecisionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ApplicationDecision.Type type, UUID applicationId, ApplicationAction action,
            ApplicationStatus fromStatus, String reason, String note, UUID actorId, Instant at) {
        repository.save(new ApplicationDecision(type, applicationId, action, fromStatus, reason, note, actorId, at));
    }
}
