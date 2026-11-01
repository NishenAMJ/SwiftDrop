package com.swiftdrop.identity.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.identity.common.ApiException;

/** The status table from the OpenAPI contract, checked exhaustively. */
class ApplicationActionTests {

    @Test
    void onlyTheDocumentedTransitionsAreAllowed() {
        for (ApplicationAction action : ApplicationAction.values()) {
            for (ApplicationStatus status : ApplicationStatus.values()) {
                boolean expected = switch (action) {
                    case SUBMIT -> false;
                    case RESUBMIT -> status == ApplicationStatus.REJECTED;
                    case APPROVE, REJECT -> status == ApplicationStatus.PENDING;
                    case SUSPEND -> status == ApplicationStatus.APPROVED;
                    case REACTIVATE -> status == ApplicationStatus.SUSPENDED;
                };
                assertThat(action.allowedFrom(status)).as("%s from %s", action, status).isEqualTo(expected);
            }
        }
    }

    @Test
    void decisionsAreRecordedButResubmissionKeepsTheLastDecision() {
        TestApplication application = new TestApplication();
        UUID dispatcher = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-11-01T10:00:00Z");

        assertThat(application.apply(ApplicationAction.REJECT, dispatcher, "Blurry ID", decidedAt))
            .isEqualTo(ApplicationStatus.PENDING);
        application.apply(ApplicationAction.RESUBMIT, UUID.randomUUID(), null, decidedAt.plusSeconds(60));

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.getSubmittedAt()).isEqualTo(decidedAt.plusSeconds(60));
        assertThat(application.lastDecision())
            .isEqualTo(new LastDecision(ApplicationStatus.REJECTED, "Blurry ID", decidedAt, dispatcher));
    }

    @Test
    void invalidTransitionIsAConflict() {
        TestApplication application = new TestApplication();
        assertThatThrownBy(() -> application.apply(ApplicationAction.SUSPEND, UUID.randomUUID(), "x", Instant.now()))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo("INVALID_STATUS_TRANSITION"));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    void rolesFollowApprovalAndSuspension() {
        assertThat(ApplicationAction.APPROVE.grantsRole()).isTrue();
        assertThat(ApplicationAction.REACTIVATE.grantsRole()).isTrue();
        assertThat(ApplicationAction.SUSPEND.revokesRole()).isTrue();
        assertThat(ApplicationAction.REJECT.grantsRole()).isFalse();
        assertThat(ApplicationAction.REJECT.revokesRole()).isFalse();
        assertThat(ApplicationAction.RESUBMIT.eventType("courier")).isEqualTo("courier.submitted");
    }

    private static final class TestApplication extends Application {
        TestApplication() {
            super(Instant.parse("2026-11-01T09:00:00Z"));
        }
    }
}
