package com.swiftdrop.delivery.warehouse.events;

import com.swiftdrop.delivery.courier.events.DeliveryEventsConfig;
import com.swiftdrop.delivery.courier.events.OutboxEvent;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelTransfer;
import com.swiftdrop.delivery.domain.Warehouse;
import com.swiftdrop.delivery.domain.WarehouseParcel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Writes customer-safe warehouse tracking events into the shared transactional outbox. */
@Component
public class WarehouseEventRecorder {

    static final String RECEIVED_AT_WAREHOUSE = "parcel.received-at-warehouse";
    static final String TRANSFERRED = "parcel.transferred";
    private static final String PRODUCER = "delivery-operations-service";

    private final OutboxEventRepository outbox;
    private final JsonMapper json;

    public WarehouseEventRecorder(OutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void receivedAtWarehouse(WarehouseParcel parcel, Warehouse warehouse, Instant occurredAt) {
        var data = baseData(parcel.getParcelId(), parcel.getParcelReference(), "AT_WAREHOUSE",
                "Received at " + warehouse.getName(), warehouse.getName());
        data.put("warehouseId", warehouse.getId().toString());
        record(RECEIVED_AT_WAREHOUSE, occurredAt, data);
    }

    public void transferred(ParcelTransfer transfer, Instant occurredAt) {
        var source = transfer.getSourceWarehouse();
        var destination = transfer.getDestinationWarehouse();
        var data = baseData(transfer.getParcelId(), transfer.getParcelReference(), "IN_TRANSIT",
                "In transit to " + destination.getName(), source.getName());
        data.put("transferId", transfer.getId().toString());
        data.put("sourceWarehouseId", source.getId().toString());
        data.put("destinationWarehouseId", destination.getId().toString());
        record(TRANSFERRED, occurredAt, data);
    }

    private LinkedHashMap<String, Object> baseData(UUID parcelId, String trackingNumber, String deliveryStatus,
                                                   String publicDescription, String publicLocation) {
        var data = new LinkedHashMap<String, Object>();
        data.put("parcelId", parcelId.toString());
        data.put("trackingNumber", trackingNumber);
        data.put("deliveryStatus", deliveryStatus);
        data.put("publicDescription", publicDescription);
        data.put("publicLocation", publicLocation);
        return data;
    }

    private void record(String eventType, Instant occurredAt, LinkedHashMap<String, Object> data) {
        var eventId = UUID.randomUUID();
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", PRODUCER);
        envelope.put("data", data);
        outbox.save(new OutboxEvent(eventId, DeliveryEventsConfig.DELIVERY_OPERATIONS_EXCHANGE,
                eventType, json.writeValueAsString(envelope), Instant.now()));
    }
}
