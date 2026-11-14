package com.swiftdrop.tracking.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.swiftdrop.tracking.tracking.TrackingProjectionUpdater;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EventMessageHandlerTests {

    @Mock
    private TrackingEventValidator eventValidator;

    @Mock
    private TrackingProjectionUpdater projectionUpdater;

    private EventMessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        messageHandler = new EventMessageHandler(new ObjectMapper(), eventValidator, projectionUpdater);
    }

    @Test
    void parsesAndProcessesAValidJsonEnvelope() {
        byte[] payload = """
                {
                  "eventId": "2be65b99-3f56-45bc-8eb3-805e0c9eb32c",
                  "eventType": "parcel.booked",
                  "version": 1,
                  "occurredAt": "2026-11-14T10:00:00Z",
                  "producer": "parcel-pricing-service",
                  "data": {
                    "parcelId": "aa552a0a-fb26-42c7-a901-585ba71d5c89",
                    "trackingNumber": "SWD-2026-100003",
                    "deliveryStatus": "BOOKED",
                    "publicDescription": "Parcel booking confirmed"
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
        when(projectionUpdater.process(any(EventEnvelope.class)))
                .thenReturn(TrackingProjectionUpdater.Result.PROCESSED);

        messageHandler.handle(payload, EventSource.PARCEL_PRICING);

        verify(eventValidator).validate(any(EventEnvelope.class), any(EventSource.class));
        verify(projectionUpdater).process(any(EventEnvelope.class));
    }

    @Test
    void rejectsMalformedJsonWithoutCallingTheProjectionUpdater() {
        byte[] payload = "not-json".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> messageHandler.handle(payload, EventSource.PARCEL_PRICING))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(projectionUpdater, never()).process(any());
    }
}
