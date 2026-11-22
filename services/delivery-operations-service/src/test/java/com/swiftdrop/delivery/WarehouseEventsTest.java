package com.swiftdrop.delivery;

import com.swiftdrop.delivery.api.CreateSortingRecordRequest;
import com.swiftdrop.delivery.api.CreateTransferRequest;
import com.swiftdrop.delivery.api.CreateWarehouseRequest;
import com.swiftdrop.delivery.api.ReceiveTransferRequest;
import com.swiftdrop.delivery.api.RecordArrivalRequest;
import com.swiftdrop.delivery.courier.events.OutboxEvent;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.courier.parcel.ParcelLookup;
import com.swiftdrop.delivery.courier.parcel.ParcelRef;
import com.swiftdrop.delivery.courier.parcel.ParcelServiceUnavailableException;
import com.swiftdrop.delivery.repository.ParcelTransferRepository;
import com.swiftdrop.delivery.repository.WarehouseParcelRepository;
import com.swiftdrop.delivery.service.WarehouseOperationsService;
import com.swiftdrop.delivery.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract tests for customer-safe warehouse events written to the shared outbox. */
@SpringBootTest
@AutoConfigureMockMvc
class WarehouseEventsTest {

    private static final String TOKEN = "dispatcher-token";

    @Autowired private WarehouseService warehouseService;
    @Autowired private WarehouseOperationsService operationsService;
    @Autowired private WarehouseParcelRepository warehouseParcels;
    @Autowired private ParcelTransferRepository transfers;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private JsonMapper json;
    @Autowired private MockMvc mvc;
    @MockitoBean private ParcelLookup parcelLookup;

    @Test
    void arrivalDispatchAndReceiptWriteExactContractEventsUsingTheDispatcherToken() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 6);
        var source = createWarehouse("SRC-" + suffix, "Colombo Hub " + suffix, "Colombo");
        var destination = createWarehouse("DST-" + suffix, "Kandy Hub " + suffix, "Kandy");
        var tracking = "SWD-2026-710001";
        var parcelId = UUID.randomUUID();
        when(parcelLookup.find(tracking, TOKEN)).thenReturn(new ParcelRef(parcelId, tracking, UUID.randomUUID()));

        mvc.perform(post("/api/v1/warehouse-parcels/arrivals")
                        .with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RecordArrivalRequest(
                                tracking, source.id(), null, "Private arrival note"))))
                .andExpect(status().isCreated());

        operationsService.sort(new CreateSortingRecordRequest(
                tracking, source.id(), destination.id(), "Kandy", "Private sorting note", null));

        var transferResponse = mvc.perform(post("/api/v1/transfers")
                        .with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateTransferRequest(
                                tracking, source.id(), destination.id()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var transferId = UUID.fromString(json.readTree(transferResponse).get("id").asString());

        operationsService.dispatch(transferId);
        operationsService.receive(transferId,
                new ReceiveTransferRequest(null, "Warehouse Officer", "Private receiving note"));

        verify(parcelLookup, times(2)).find(eq(tracking), eq(TOKEN));
        assertThat(warehouseParcels.findByParcelReferenceIgnoreCase(tracking).orElseThrow().getParcelId())
                .isEqualTo(parcelId);
        assertThat(transfers.findById(transferId).orElseThrow().getParcelId()).isEqualTo(parcelId);

        var events = eventsFor(tracking);
        assertThat(events).extracting(OutboxEvent::getRoutingKey).containsExactly(
                "parcel.received-at-warehouse", "parcel.transferred", "parcel.received-at-warehouse");
        assertThat(events).extracting(OutboxEvent::getExchange).containsOnly("swiftdrop.delivery-operations");

        assertReceivedEvent(events.get(0), parcelId, tracking, source.id(), source.name());
        assertTransferredEvent(events.get(1), parcelId, tracking, transferId,
                source.id(), destination.id(), source.name(), destination.name());
        assertReceivedEvent(events.get(2), parcelId, tracking, destination.id(), destination.name());

        for (var event : events) {
            assertEnvelope(event);
            assertThat(event.getPayload())
                    .doesNotContain("1 Private Street")
                    .doesNotContain("+94 11 555 0101")
                    .doesNotContain("Private arrival note")
                    .doesNotContain("Private sorting note")
                    .doesNotContain("Private receiving note")
                    .doesNotContain("Warehouse Officer")
                    .doesNotContain("receivedBy");
        }
    }

    @Test
    void sortingCreatingAndCancellingATransferDoNotWriteEvents() {
        var suffix = UUID.randomUUID().toString().substring(0, 6);
        var source = createWarehouse("NSA-" + suffix, "Negombo Hub " + suffix, "Negombo");
        var destination = createWarehouse("NSB-" + suffix, "Galle Hub " + suffix, "Galle");
        var tracking = "SWD-2026-710002";
        var parcelId = UUID.randomUUID();
        when(parcelLookup.find(tracking, TOKEN)).thenReturn(new ParcelRef(parcelId, tracking, UUID.randomUUID()));

        operationsService.recordArrival(new RecordArrivalRequest(tracking, source.id(), null, null), TOKEN);
        operationsService.sort(new CreateSortingRecordRequest(
                tracking, source.id(), destination.id(), "Galle", null, null));
        var transfer = operationsService.createTransfer(
                new CreateTransferRequest(tracking, source.id(), destination.id()), TOKEN);
        operationsService.cancel(transfer.id());

        assertThat(eventsFor(tracking)).extracting(OutboxEvent::getRoutingKey)
                .containsExactly("parcel.received-at-warehouse");
    }

    @Test
    void warehouseArrivalReturns503WhenParcelAndPricingIsUnavailable() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 6);
        var warehouse = createWarehouse("ERR-" + suffix, "Matara Hub " + suffix, "Matara");
        var tracking = "SWD-2026-710003";
        when(parcelLookup.find(tracking, TOKEN)).thenThrow(
                new ParcelServiceUnavailableException("connection refused", new RuntimeException("offline")));

        mvc.perform(post("/api/v1/warehouse-parcels/arrivals")
                        .with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RecordArrivalRequest(
                                tracking, warehouse.id(), null, null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Parcel service unavailable"))
                .andExpect(jsonPath("$.detail").value("The parcel could not be checked right now. Please try again."));
    }

    private void assertEnvelope(OutboxEvent event) {
        var body = parse(event);
        assertThat(names(body)).containsExactlyInAnyOrder(
                "eventId", "eventType", "version", "occurredAt", "producer", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(event.getId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo(event.getRoutingKey());
        assertThat(body.get("version").asInt()).isEqualTo(1);
        assertThat(body.get("producer").asString()).isEqualTo("delivery-operations-service");
        assertThat(Instant.parse(body.get("occurredAt").asString())).isNotNull();
    }

    private void assertReceivedEvent(OutboxEvent event, UUID parcelId, String tracking,
                                     UUID warehouseId, String warehouseName) {
        var data = parse(event).get("data");
        assertThat(names(data)).containsExactlyInAnyOrder(
                "parcelId", "trackingNumber", "deliveryStatus", "publicDescription", "publicLocation", "warehouseId");
        assertThat(data.get("parcelId").asString()).isEqualTo(parcelId.toString());
        assertThat(data.get("trackingNumber").asString()).isEqualTo(tracking);
        assertThat(data.get("deliveryStatus").asString()).isEqualTo("AT_WAREHOUSE");
        assertThat(data.get("warehouseId").asString()).isEqualTo(warehouseId.toString());
        assertThat(data.get("publicLocation").asString()).isEqualTo(warehouseName);
        assertThat(data.get("publicDescription").asString()).hasSizeLessThanOrEqualTo(200);
        assertThat(data.get("publicLocation").asString()).hasSizeLessThanOrEqualTo(120);
    }

    private void assertTransferredEvent(OutboxEvent event, UUID parcelId, String tracking, UUID transferId,
                                        UUID sourceId, UUID destinationId, String sourceName, String destinationName) {
        var data = parse(event).get("data");
        assertThat(names(data)).containsExactlyInAnyOrder(
                "parcelId", "trackingNumber", "deliveryStatus", "publicDescription", "publicLocation",
                "transferId", "sourceWarehouseId", "destinationWarehouseId");
        assertThat(data.get("parcelId").asString()).isEqualTo(parcelId.toString());
        assertThat(data.get("trackingNumber").asString()).isEqualTo(tracking);
        assertThat(data.get("deliveryStatus").asString()).isEqualTo("IN_TRANSIT");
        assertThat(data.get("transferId").asString()).isEqualTo(transferId.toString());
        assertThat(data.get("sourceWarehouseId").asString()).isEqualTo(sourceId.toString());
        assertThat(data.get("destinationWarehouseId").asString()).isEqualTo(destinationId.toString());
        assertThat(data.get("publicLocation").asString()).isEqualTo(sourceName);
        assertThat(data.get("publicDescription").asString()).isEqualTo("In transit to " + destinationName);
        assertThat(data.get("publicDescription").asString()).hasSizeLessThanOrEqualTo(200);
        assertThat(data.get("publicLocation").asString()).hasSizeLessThanOrEqualTo(120);
    }

    private Set<String> names(JsonNode node) {
        var names = new HashSet<String>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private List<OutboxEvent> eventsFor(String tracking) {
        return outbox.findAllByOrderByCreatedAtAsc().stream()
                .filter(event -> event.getPayload().contains("\"trackingNumber\":\"" + tracking + "\""))
                .toList();
    }

    private JsonNode parse(OutboxEvent event) {
        return json.readTree(event.getPayload());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor dispatcher() {
        return jwt().jwt(token -> token.tokenValue(TOKEN).subject(UUID.randomUUID().toString())
                        .claim("roles", List.of("DISPATCHER")))
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }

    private com.swiftdrop.delivery.api.WarehouseResponse createWarehouse(String code, String name, String city) {
        return warehouseService.create(new CreateWarehouseRequest(code, name, "1 Private Street", null, city,
                city, null, "Private Contact", "+94 11 555 0101", "private@example.com"));
    }
}
