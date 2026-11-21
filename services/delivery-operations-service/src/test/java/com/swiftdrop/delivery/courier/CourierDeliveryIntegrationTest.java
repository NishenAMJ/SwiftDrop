package com.swiftdrop.delivery.courier;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.AssignmentResponse;
import com.swiftdrop.delivery.courier.api.CourierCategoryResponse;
import com.swiftdrop.delivery.courier.api.CourierResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordAttemptRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.ReportIssueRequest;
import com.swiftdrop.delivery.courier.api.ReviewIssueRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.CourierStatus;
import com.swiftdrop.delivery.courier.domain.FailureReason;
import com.swiftdrop.delivery.courier.domain.IssueStatus;
import com.swiftdrop.delivery.courier.domain.IssueType;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import com.swiftdrop.delivery.courier.service.DeliveryIssueService;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CourierDeliveryIntegrationTest {

    @Autowired
    private CourierCategoryService categoryService;

    @Autowired
    private CourierService courierService;

    @Autowired
    private DeliveryAssignmentService assignmentService;

    @Autowired
    private DeliveryIssueService issueService;

    @Test
    void parcelIsDeliveredThroughAllStatusesWithProof() {
        var courier = createCourier("DLV-BIKE", "Nimal Perera");

        var assignment = assign(
                new AssignParcelRequest("swd-2026-000201", courier.id(), "Dispatcher", null));
        assertThat(assignment.status()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.parcelReference()).isEqualTo("SWD-2026-000201");

        for (var next : new AssignmentStatus[]{AssignmentStatus.PICKED_UP, AssignmentStatus.IN_TRANSIT,
                AssignmentStatus.OUT_FOR_DELIVERY}) {
            assignment = assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
            assertThat(assignment.status()).isEqualTo(next);
        }

        var proof = assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Kamala Silva", "Handed over at the gate", null, null));
        assertThat(proof.recipientName()).isEqualTo("Kamala Silva");
        assertThat(assignmentService.get(assignment.id()).status()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(assignmentService.get(assignment.id()).completedAt()).isNotNull();
    }

    @Test
    void failedAttemptMarksDeliveryFailedAndAllowsReassignment() {
        var courier = createCourier("DLV-VAN", "Sunil Fernando");
        var assignment = assign(
                new AssignParcelRequest("SWD-2026-000202", courier.id(), "Dispatcher", null));
        assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));
        assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.OUT_FOR_DELIVERY, null));

        var attempt = assignmentService.recordFailedAttempt(assignment.id(),
                new RecordAttemptRequest(FailureReason.RECIPIENT_UNAVAILABLE, "Nobody home", null));

        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(assignmentService.get(assignment.id()).status()).isEqualTo(AssignmentStatus.DELIVERY_FAILED);

        var retry = assign(
                new AssignParcelRequest("SWD-2026-000202", courier.id(), "Dispatcher", "Second attempt"));
        assertThat(retry.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void statusCannotSkipStepsOrBypassProofAndAttempts() {
        var courier = createCourier("DLV-SKIP", "Ruwan Jayasuriya");
        var assignment = assign(
                new AssignParcelRequest("SWD-2026-000203", courier.id(), "Dispatcher", null));

        assertThatThrownBy(() -> assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.OUT_FOR_DELIVERY, null)))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.DELIVERED, null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("proof");
        assertThatThrownBy(() -> assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Someone", null, null, null)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void parcelCannotHaveTwoActiveAssignments() {
        var first = createCourier("DLV-DUP1", "Courier One");
        var second = createCourier("DLV-DUP2", "Courier Two");
        assign(new AssignParcelRequest("SWD-2026-000204", first.id(), "Dispatcher", null));

        assertThatThrownBy(() -> assign(
                new AssignParcelRequest("swd-2026-000204", second.id(), "Dispatcher", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyAvailableCouriersCanBeAssigned() {
        var courier = createCourier("DLV-OFF", "Off Duty Courier");
        courierService.changeStatus(courier.id(), CourierStatus.OFF_DUTY);

        assertThatThrownBy(() -> assign(
                new AssignParcelRequest("SWD-2026-000205", courier.id(), "Dispatcher", null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void duplicateCategoryCodesAndCourierIdentitiesAreRejected() {
        var category = createCategory("DUP-CAT");
        assertThatThrownBy(() -> createCategory("dup-cat")).isInstanceOf(ConflictException.class);

        var identity = UUID.randomUUID();
        courierService.create(new CreateCourierRequest(identity, "First", "+94 77 000 0000", null, category.id()));
        assertThatThrownBy(() -> courierService.create(
                new CreateCourierRequest(identity, "Second", "+94 77 000 0001", null, category.id())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void courierReportsIssueAndDispatcherReviewsIt() {
        var courier = createCourier("DLV-ISS", "Issue Courier");
        var assignment = assign(
                new AssignParcelRequest("SWD-2026-000206", courier.id(), "Dispatcher", null));

        var issue = issueService.report(new ReportIssueRequest(courier.id(), assignment.id(), null,
                IssueType.DAMAGED_PARCEL, "Box crushed during pickup"));
        assertThat(issue.status()).isEqualTo(IssueStatus.OPEN);
        assertThat(issue.parcelReference()).isEqualTo("SWD-2026-000206");

        var reviewed = issueService.review(issue.id(), new ReviewIssueRequest(IssueStatus.RESOLVED, "Dispatcher",
                "Customer informed"));
        assertThat(reviewed.status()).isEqualTo(IssueStatus.RESOLVED);

        assertThatThrownBy(() -> issueService.review(issue.id(),
                new ReviewIssueRequest(IssueStatus.DISMISSED, "Dispatcher", null)))
                .isInstanceOf(InvalidOperationException.class);
    }

    private AssignmentResponse assign(AssignParcelRequest request) {
        return assignmentService.assign(request, "test-token");
    }

    private CourierCategoryResponse createCategory(String code) {
        return categoryService.create(new CreateCourierCategoryRequest(code, "Category " + code, null,
                new BigDecimal("50.00")));
    }

    private CourierResponse createCourier(String categoryCode, String name) {
        var category = createCategory(categoryCode);
        return courierService.create(new CreateCourierRequest(UUID.randomUUID(), name, "+94 77 123 4567",
                "WP-1234", category.id()));
    }
}
