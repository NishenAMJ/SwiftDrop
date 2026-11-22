package com.swiftdrop.delivery.courier;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.courier.parcel.ParcelContact;
import com.swiftdrop.delivery.courier.parcel.ParcelLookup;
import com.swiftdrop.delivery.courier.parcel.ParcelRef;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** The courier gets who to meet and where while a job is open; the details are not kept in the API afterwards. */
@SpringBootTest
class DeliveryContactsTest {

    @Autowired private CourierCategoryService categoryService;
    @Autowired private CourierService courierService;
    @Autowired private DeliveryAssignmentService assignmentService;
    @Autowired private OutboxEventRepository outbox;
    @MockitoBean private ParcelLookup parcelLookup;

    @Test
    void theAssignedJobCarriesBothContactsWhileOpenAndNeitherOnceDelivered() {
        var tracking = "SWD-2026-000951";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID(), null, null, null, null,
                new ParcelContact("Nimal", "0771234567", "12 Main St, Colombo"),
                new ParcelContact("Kamala", "0777654321", "45 Lake Rd, Kandy, 20000")));
        var assignment = assignmentService.assign(
                new AssignParcelRequest(tracking, createCourier("CON-A").id(), "Dispatcher", null), "token");

        assertThat(assignment.pickup()).isEqualTo(new ParcelContact("Nimal", "0771234567", "12 Main St, Colombo"));
        assertThat(assignment.delivery().phone()).isEqualTo("0777654321");
        assertThat(assignmentService.get(assignment.id()).delivery().address()).isEqualTo("45 Lake Rd, Kandy, 20000");

        for (var next : new AssignmentStatus[]{AssignmentStatus.PICKED_UP, AssignmentStatus.OUT_FOR_DELIVERY}) {
            assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
        }
        assignmentService.recordProof(assignment.id(), new RecordProofRequest("Kamala", null, null, null, null));

        var finished = assignmentService.get(assignment.id());
        assertThat(finished.pickup()).isNull();
        assertThat(finished.delivery()).isNull();
    }

    @Test
    void neverLeavesThroughAnEvent() {
        var tracking = "SWD-2026-000952";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID(), null, null, null, null,
                new ParcelContact("Nimal", "0771234567", "12 Main St, Colombo"),
                new ParcelContact("Kamala", "0777654321", "45 Lake Rd, Kandy")));
        var assignment = assignmentService.assign(
                new AssignParcelRequest(tracking, createCourier("CON-B").id(), "Dispatcher", null), "token");
        assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));

        var payloads = outbox.findAllByOrderByCreatedAtAsc().stream()
                .map(event -> event.getPayload()).filter(body -> body.contains(tracking)).toList();

        assertThat(payloads).isNotEmpty();
        assertThat(payloads).noneMatch(body -> body.contains("0777654321") || body.contains("Lake Rd")
                || body.contains("Kamala") || body.contains("0771234567"));
    }

    @Test
    void aParcelWithoutAddressesStillAssigns() {
        var tracking = "SWD-2026-000953";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID()));

        var assignment = assignmentService.assign(
                new AssignParcelRequest(tracking, createCourier("CON-C").id(), "Dispatcher", null), "token");

        assertThat(assignment.pickup()).isNull();
        assertThat(assignment.delivery()).isNull();
    }

    private com.swiftdrop.delivery.courier.api.CourierResponse createCourier(String code) {
        var category = categoryService.create(new CreateCourierCategoryRequest(code, "Category " + code, null,
                new BigDecimal("50.00")));
        return courierService.create(new CreateCourierRequest(UUID.randomUUID(), "Courier " + code,
                "+94 77 123 4567", null, category.id()));
    }
}
