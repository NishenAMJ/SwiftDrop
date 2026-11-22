package com.swiftdrop.delivery.courier;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.events.OutboxEvent;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.courier.parcel.ParcelLookup;
import com.swiftdrop.delivery.courier.parcel.ParcelRef;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Merchant Cash on Delivery: the cash is recorded with the proof and reported to Payment & Settlement. */
@SpringBootTest
class CashOnDeliveryTest {

    private static final UUID MERCHANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void customerDeliveryFeeRequiresFullCashAndUsesSeparateEvent() {
        var tracking = "SWD-2026-000906";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID(), null, new BigDecimal("450.00"), "SNP-CUSTOMER", "LKR"));
        var assignment = outForDelivery(tracking, createCourier("COD-F").id());
        assertThat(assignment.codAmount()).isEqualByComparingTo("450.00");
        assertThatThrownBy(() -> assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Customer", null, null, null, new BigDecimal("400.00"))))
                .isInstanceOf(InvalidOperationException.class);
        assertThat(assignmentService.get(assignment.id()).status()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Customer", null, null, null, new BigDecimal("450.00")));
        assertThat(codEventsFor(tracking)).isEmpty();
        var customerEvents = outbox.findAllByOrderByCreatedAtAsc().stream()
                .filter(event -> event.getRoutingKey().equals("customer-cod.collected"))
                .filter(event -> event.getPayload().contains(tracking)).toList();
        assertThat(customerEvents).hasSize(1);
        assertThat(json.readTree(customerEvents.getFirst().getPayload()).get("data").has("merchantId")).isFalse();
    }

    @Autowired private CourierCategoryService categoryService;
    @Autowired private CourierService courierService;
    @Autowired private DeliveryAssignmentService assignmentService;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private JsonMapper json;
    @MockitoBean private ParcelLookup parcelLookup;

    @Test
    void deliveringACodParcelRecordsTheCashAndPublishesTheContractEvent() {
        var tracking = "SWD-2026-000901";
        var parcelId = UUID.randomUUID();
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(parcelId, tracking, UUID.randomUUID(),
                MERCHANT, new BigDecimal("2500.00"), "SNP-ABCDEF0123456789", "LKR"));
        var courier = createCourier("COD-A");
        var assignment = outForDelivery(tracking, courier.id());
        assertThat(assignment.codAmount()).isEqualByComparingTo("2500.00");

        var proof = assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Kamala Silva", null, null, null, new BigDecimal("2500.00")));

        assertThat(proof.collectedAmount()).isEqualByComparingTo("2500.00");
        var events = codEventsFor(tracking);
        assertThat(events).hasSize(1);
        var body = json.readTree(events.get(0).getPayload());
        assertThat(body.get("eventType").asString()).isEqualTo("delivery.cod-collected");
        assertThat(body.get("version").asInt()).isEqualTo(1);
        assertThat(body.get("producer").asString()).isEqualTo("delivery-operations-service");
        var data = body.get("data");
        assertThat(data.get("bookingId").asString()).isEqualTo(parcelId.toString());
        assertThat(data.get("trackingNumber").asString()).isEqualTo(tracking);
        assertThat(data.get("pricingSnapshotId").asString()).isEqualTo("SNP-ABCDEF0123456789");
        assertThat(data.get("merchantId").asString()).isEqualTo(MERCHANT.toString());
        assertThat(data.get("courierId").asString()).isEqualTo(courier.id().toString());
        assertThat(data.get("deliveryAttemptId").asString()).isEqualTo(proof.id().toString());
        assertThat(data.get("paymentMethod").asString()).isEqualTo("COD");
        assertThat(data.get("expectedAmount").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(data.get("collectedAmount").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(data.get("currency").asString()).isEqualTo("LKR");
        assertThat(data.get("collectedAt").asString()).isNotBlank();
        // Only the fields in the contract: no recipient name, no courier notes.
        assertThat(data.propertyNames()).containsExactlyInAnyOrder("bookingId", "trackingNumber",
                "pricingSnapshotId", "merchantId", "courierId", "deliveryAttemptId", "paymentMethod",
                "expectedAmount", "collectedAmount", "currency", "collectedAt");
    }

    @Test
    void aCodParcelCannotBeDeliveredWithoutSayingHowMuchWasCollected() {
        var tracking = "SWD-2026-000902";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID(), MERCHANT, new BigDecimal("900.00"), "SNP-1", "LKR"));
        var assignment = outForDelivery(tracking, createCourier("COD-B").id());

        assertThatThrownBy(() -> assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Kamala Silva", null, null, null, null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cash on Delivery");

        assertThat(codEventsFor(tracking)).isEmpty();
        assertThat(assignmentService.get(assignment.id()).status()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void aParcelThatIsNotCodRefusesAnyCashAmount() {
        var tracking = "SWD-2026-000903";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID()));
        var assignment = outForDelivery(tracking, createCourier("COD-C").id());
        assertThat(assignment.codAmount()).isNull();

        assertThatThrownBy(() -> assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Kamala Silva", null, null, null, new BigDecimal("100.00"))))
                .isInstanceOf(InvalidOperationException.class);

        assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Kamala Silva", null, null, null, null));
        assertThat(codEventsFor(tracking)).isEmpty();
    }

    @Test
    void aCustomerCodBookingWithoutAMerchantAmountIsNotTreatedAsCollectedCash() {
        var tracking = "SWD-2026-000904";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID(), null, null, "SNP-2", "LKR"));
        var assignment = outForDelivery(tracking, createCourier("COD-D").id());

        assignmentService.recordProof(assignment.id(),
                new RecordProofRequest("Kamala Silva", null, null, null, null));

        assertThat(codEventsFor(tracking)).isEmpty();
    }

    @Test
    void merchantOrdersCarryTheMerchantOnTheCustomerStatusEventToo() {
        var tracking = "SWD-2026-000905";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(new ParcelRef(UUID.randomUUID(), tracking,
                UUID.randomUUID(), MERCHANT, null, "SNP-3", "LKR"));
        var assignment = outForDelivery(tracking, createCourier("COD-E").id());

        var statusEvents = outbox.findAllByOrderByCreatedAtAsc().stream()
                .filter(event -> event.getRoutingKey().equals("parcel.status-changed"))
                .filter(event -> event.getPayload().contains("\"trackingNumber\":\"" + tracking + "\""))
                .toList();

        assertThat(statusEvents).isNotEmpty();
        for (var event : statusEvents) {
            assertThat(json.readTree(event.getPayload()).get("data").get("merchantId").asString())
                    .isEqualTo(MERCHANT.toString());
        }
        assertThat(assignment.codAmount()).isNull();
    }

    private com.swiftdrop.delivery.courier.api.AssignmentResponse outForDelivery(String tracking, UUID courierId) {
        var assignment = assignmentService.assign(new AssignParcelRequest(tracking, courierId, "Dispatcher", null),
                "token");
        for (var next : List.of(AssignmentStatus.PICKED_UP, AssignmentStatus.OUT_FOR_DELIVERY)) {
            assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
        }
        return assignmentService.get(assignment.id());
    }

    private List<OutboxEvent> codEventsFor(String tracking) {
        return outbox.findAllByOrderByCreatedAtAsc().stream()
                .filter(event -> event.getRoutingKey().equals("delivery.cod-collected"))
                .filter(event -> event.getPayload().contains("\"trackingNumber\":\"" + tracking + "\""))
                .toList();
    }

    private com.swiftdrop.delivery.courier.api.CourierResponse createCourier(String code) {
        var category = categoryService.create(new CreateCourierCategoryRequest(code, "Category " + code, null,
                new BigDecimal("50.00")));
        return courierService.create(new CreateCourierRequest(UUID.randomUUID(), "Courier " + code,
                "+94 77 123 4567", null, category.id()));
    }
}
