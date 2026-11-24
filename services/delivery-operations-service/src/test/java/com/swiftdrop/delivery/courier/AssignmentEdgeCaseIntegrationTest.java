package com.swiftdrop.delivery.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.AssignmentResponse;
import com.swiftdrop.delivery.courier.api.CourierResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordAttemptRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.ReportIssueRequest;
import com.swiftdrop.delivery.courier.api.ReviewIssueRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.api.UpdateCourierRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.CategoryStatus;
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
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/** Courier assignment, reassignment, proof and issue edge cases against the H2 test database. */
@SpringBootTest
class AssignmentEdgeCaseIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger(300);

    @Autowired private CourierCategoryService categories;
    @Autowired private CourierService couriers;
    @Autowired private DeliveryAssignmentService assignments;
    @Autowired private DeliveryIssueService issues;

    private UUID category() {
        return categories.create(new CreateCourierCategoryRequest("AEC-" + SEQ.incrementAndGet(), "Bike", null,
                new BigDecimal("50.00"))).id();
    }

    private CourierResponse courier(UUID categoryId) {
        return couriers.create(new CreateCourierRequest(UUID.randomUUID(), "Courier " + SEQ.incrementAndGet(),
                "+94 77 123 4567", "WP-1234", categoryId));
    }

    private static String reference() {
        return "SWD-2026-" + (800000 + SEQ.incrementAndGet());
    }

    private AssignmentResponse assign(CourierResponse courier) {
        return assignments.assign(new AssignParcelRequest(reference(), courier.id(), "Dispatcher", null), "token");
    }

    private AssignmentResponse outForDelivery(CourierResponse courier) {
        var assignment = assign(courier);
        assignments.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));
        return assignments.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(AssignmentStatus.OUT_FOR_DELIVERY, null));
    }

    @Test
    void courierInAnInactiveCategoryCannotBeAssigned() {
        UUID categoryId = category();
        var courier = courier(categoryId);
        categories.changeStatus(categoryId, CategoryStatus.INACTIVE);
        assertThatThrownBy(() -> assign(courier)).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("category is not active");
    }

    @Test
    void assigningAnUnknownCourierIsNotFound() {
        assertThatThrownBy(() -> assignments.assign(new AssignParcelRequest(reference(), UUID.randomUUID(), "Dispatcher", null), "token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deliveryFailedCanOnlyBeReachedByRecordingAnAttempt() {
        var assignment = outForDelivery(courier(category()));
        assertThatThrownBy(() -> assignments.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.DELIVERY_FAILED, null)))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("failed delivery attempt");

        assignments.recordFailedAttempt(assignment.id(), new RecordAttemptRequest(FailureReason.INCORRECT_ADDRESS, "No such lane", null));
        assertThat(assignments.listAttempts(assignment.id())).hasSize(1);
        assertThat(assignments.listAttempts(assignment.id()).getFirst().attemptNumber()).isEqualTo(1);
    }

    @Test
    void deliveredParcelCannotMoveAgain() {
        var assignment = outForDelivery(courier(category()));
        assignments.recordProof(assignment.id(), new RecordProofRequest("Kamala", null, null, null, null));
        assertThatThrownBy(() -> assignments.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.IN_TRANSIT, null)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void proofOfDeliveryCanOnlyBeRecordedOnce() {
        var assignment = outForDelivery(courier(category()));
        assignments.recordProof(assignment.id(), new RecordProofRequest("Kamala", null, null, null, null));
        assertThatThrownBy(() -> assignments.recordProof(assignment.id(), new RecordProofRequest("Someone else", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(assignments.getProof(assignment.id()).recipientName()).isEqualTo("Kamala");
    }

    @Test
    void nonCodParcelCannotRecordCollectedCash() {
        var assignment = outForDelivery(courier(category()));
        assertThatThrownBy(() -> assignments.recordProof(assignment.id(),
                new RecordProofRequest("Kamala", null, null, null, new BigDecimal("100.00"))))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("not Cash on Delivery");
    }

    @Test
    void missingProofOrProofFileIsNotFound() {
        var assignment = assign(courier(category()));
        assertThatThrownBy(() -> assignments.getProof(assignment.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> assignments.getProofFile(assignment.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> assignments.listAttempts(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reassignmentAfterAFailedAttemptGoesToAnotherCourier() {
        UUID categoryId = category();
        var first = courier(categoryId);
        var second = courier(categoryId);
        String ref = reference();
        var assignment = assignments.assign(new AssignParcelRequest(ref, first.id(), "Dispatcher", null), "token");
        assignments.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));
        assignments.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(AssignmentStatus.OUT_FOR_DELIVERY, null));
        assignments.recordFailedAttempt(assignment.id(), new RecordAttemptRequest(FailureReason.RECIPIENT_UNAVAILABLE, null, null));

        var retry = assignments.assign(new AssignParcelRequest(ref, second.id(), "Dispatcher", "Second courier"), "token");
        assertThat(retry.courierId()).isEqualTo(second.id());
        assertThatThrownBy(() -> assignments.assign(new AssignParcelRequest(ref, first.id(), "Dispatcher", null), "token"))
                .as("still only one active assignment").isInstanceOf(ConflictException.class);
    }

    @Test
    void assignmentsCanBeFilteredByCourierAndStatus() {
        var courier = courier(category());
        var assigned = assign(courier);
        var moving = assign(courier);
        assignments.updateStatus(moving.id(), new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));
        var page = PageRequest.of(0, 100);

        assertThat(assignments.list(null, courier.id(), null, page).getTotalElements()).isEqualTo(2);
        assertThat(assignments.list(null, courier.id(), AssignmentStatus.PICKED_UP, page).getContent())
                .extracting(a -> a.id()).containsExactly(moving.id());
        assertThat(assignments.list(null, null, AssignmentStatus.ASSIGNED, page).getContent())
                .extracting(a -> a.id()).contains(assigned.id()).doesNotContain(moving.id());
        assertThat(assignments.list(assigned.parcelReference().toLowerCase(), null, null, page).getContent())
                .extracting(a -> a.id()).containsExactly(assigned.id());
        assertThat(assignments.list(null, null, null, page).getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void courierCannotReportAnIssueOnAnotherCouriersAssignment() {
        UUID categoryId = category();
        var owner = courier(categoryId);
        var other = courier(categoryId);
        var assignment = assign(owner);
        assertThatThrownBy(() -> issues.report(new ReportIssueRequest(other.id(), assignment.id(), null,
                IssueType.DAMAGED_PARCEL, "Not mine")))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("does not belong");
    }

    @Test
    void issueCannotBeReopenedAndListsCanBeFiltered() {
        var courier = courier(category());
        var issue = issues.report(new ReportIssueRequest(courier.id(), null, " swd-2026-123456 ",
                IssueType.DAMAGED_PARCEL, "Wet box"));
        assertThat(issue.parcelReference()).isEqualTo("SWD-2026-123456");
        assertThatThrownBy(() -> issues.review(issue.id(), new ReviewIssueRequest(IssueStatus.OPEN, "Dispatcher", null)))
                .isInstanceOf(InvalidOperationException.class);

        issues.review(issue.id(), new ReviewIssueRequest(IssueStatus.UNDER_REVIEW, "Dispatcher", " "));
        var page = PageRequest.of(0, 100);
        assertThat(issues.list(courier.id(), IssueStatus.UNDER_REVIEW, page).getContent()).extracting(i -> i.id())
                .containsExactly(issue.id());
        assertThat(issues.list(courier.id(), null, page).getTotalElements()).isEqualTo(1);
        assertThat(issues.list(null, IssueStatus.UNDER_REVIEW, page).getContent()).extracting(i -> i.id()).contains(issue.id());
        assertThat(issues.list(null, null, page).getTotalElements()).isPositive();
    }

    @Test
    void courierProfileUpdatesAndFilters() {
        UUID categoryId = category();
        var courier = courier(categoryId);
        UUID otherCategory = category();

        var updated = couriers.update(courier.id(), new UpdateCourierRequest(" Renamed ", " +94 77 999 9999 ", " ", otherCategory));
        assertThat(updated.fullName()).isEqualTo("Renamed");
        assertThat(couriers.get(courier.id()).categoryId()).isEqualTo(otherCategory);

        categories.changeStatus(categoryId, CategoryStatus.INACTIVE);
        assertThatThrownBy(() -> couriers.update(courier.id(), new UpdateCourierRequest("Renamed", "+94 77 999 9999", null, categoryId)))
                .isInstanceOf(InvalidOperationException.class);

        var page = PageRequest.of(0, 100);
        assertThat(couriers.list(CourierStatus.AVAILABLE, otherCategory, page).getContent()).extracting(c -> c.id())
                .containsExactly(courier.id());
        assertThat(couriers.list(null, otherCategory, page).getTotalElements()).isEqualTo(1);
        assertThat(couriers.list(CourierStatus.AVAILABLE, null, page).getContent()).extracting(c -> c.id()).contains(courier.id());
    }
}
