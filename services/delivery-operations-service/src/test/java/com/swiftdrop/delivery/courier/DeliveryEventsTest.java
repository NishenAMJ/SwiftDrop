package com.swiftdrop.delivery.courier;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordAttemptRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.FailureReason;
import com.swiftdrop.delivery.courier.events.OutboxEvent;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Every delivery step writes an event that matches delivery-status-events.schema.json. */
@SpringBootTest
class DeliveryEventsTest {

    @Autowired
    private CourierCategoryService categoryService;
    @Autowired
    private CourierService courierService;
    @Autowired
    private DeliveryAssignmentService assignmentService;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private JsonMapper json;

    @Test
    void deliveredParcelWritesOneEventPerStepWithTheContractFields() {
        var courier = createCourier("EVT-A");
        var tracking = "SWD-2026-000401";
        var assignment = assignmentService.assign(
                new AssignParcelRequest(tracking, courier.id(), "Dispatcher", null), "token");
        for (var next : List.of(AssignmentStatus.PICKED_UP, AssignmentStatus.IN_TRANSIT,
                AssignmentStatus.OUT_FOR_DELIVERY)) {
            assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
        }
        assignmentService.recordProof(assignment.id(), new RecordProofRequest("Kamala Silva", null, null, null));

        var events = eventsFor(tracking);

        assertThat(events).extracting(OutboxEvent::getRoutingKey).containsExactly(
                "delivery.assigned", "delivery.picked-up", "delivery.in-transit",
                "delivery.out-for-delivery", "delivery.delivered");
        var expectedParcelId = UUID.nameUUIDFromBytes(tracking.getBytes(StandardCharsets.UTF_8)).toString();
        for (var event : events) {
            var body = parse(event);
            assertThat(body.get("eventId").asString()).isEqualTo(event.getId().toString());
            assertThat(body.get("eventType").asString()).isEqualTo(event.getRoutingKey());
            assertThat(body.get("version").asInt()).isEqualTo(1);
            assertThat(body.get("producer").asString()).isEqualTo("delivery-operations-service");
            assertThat(body.get("occurredAt").asString()).isNotBlank();
            var data = body.get("data");
            assertThat(data.get("parcelId").asString()).isEqualTo(expectedParcelId);
            assertThat(data.get("trackingNumber").asString()).isEqualTo(tracking);
            assertThat(data.get("assignmentId").asString()).isEqualTo(assignment.id().toString());
            assertThat(data.get("courierId").asString()).isEqualTo(courier.id().toString());
            assertThat(data.get("publicDescription").asString()).isNotBlank();
        }
        assertThat(parse(events.get(0)).get("data").get("deliveryStatus").asString()).isEqualTo("ASSIGNED");
        assertThat(parse(events.get(4)).get("data").get("deliveryStatus").asString()).isEqualTo("DELIVERED");
    }

    @Test
    void failedDeliveryCarriesTheAttemptAndSafeFailureCodeButNeverTheCourierNotes() {
        var courier = createCourier("EVT-B");
        var tracking = "SWD-2026-000402";
        var assignment = assignmentService.assign(
                new AssignParcelRequest(tracking, courier.id(), "Dispatcher", null), "token");
        assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));
        assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.OUT_FOR_DELIVERY, null));

        var attempt = assignmentService.recordFailedAttempt(assignment.id(), new RecordAttemptRequest(
                FailureReason.RECIPIENT_UNAVAILABLE, "Dog in the garden, neighbour said call after 6pm", null));

        var failed = eventsFor(tracking).stream()
                .filter(event -> event.getRoutingKey().equals("delivery.failed")).findFirst().orElseThrow();
        var data = parse(failed).get("data");
        assertThat(data.get("deliveryStatus").asString()).isEqualTo("DELIVERY_FAILED");
        assertThat(data.get("attemptId").asString()).isEqualTo(attempt.id().toString());
        assertThat(data.get("failureCode").asString()).isEqualTo("RECIPIENT_UNAVAILABLE");
        assertThat(failed.getPayload()).doesNotContain("Dog in the garden");
    }

    @Test
    void rejectedChangesLeaveNoEventBehind() {
        var courier = createCourier("EVT-C");
        var tracking = "SWD-2026-000403";
        var assignment = assignmentService.assign(
                new AssignParcelRequest(tracking, courier.id(), "Dispatcher", null), "token");

        try {
            assignmentService.updateStatus(assignment.id(),
                    new UpdateAssignmentStatusRequest(AssignmentStatus.OUT_FOR_DELIVERY, null));
        } catch (RuntimeException expected) {
            // an ASSIGNED parcel cannot skip ahead
        }

        assertThat(eventsFor(tracking)).extracting(OutboxEvent::getRoutingKey).containsExactly("delivery.assigned");
    }

    private List<OutboxEvent> eventsFor(String tracking) {
        return outbox.findAllByOrderByCreatedAtAsc().stream()
                .filter(event -> event.getPayload().contains("\"trackingNumber\":\"" + tracking + "\""))
                .toList();
    }

    private JsonNode parse(OutboxEvent event) {
        return json.readTree(event.getPayload());
    }

    private com.swiftdrop.delivery.courier.api.CourierResponse createCourier(String code) {
        var category = categoryService.create(new CreateCourierCategoryRequest(code, "Category " + code, null,
                new BigDecimal("50.00")));
        return courierService.create(new CreateCourierRequest(UUID.randomUUID(), "Courier " + code,
                "+94 77 123 4567", null, category.id()));
    }
}
