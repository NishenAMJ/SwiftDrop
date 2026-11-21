package com.swiftdrop.delivery.courier.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

/** The cancellation message either stops the job or, if it can never be understood, goes to the dead-letter queue. */
class ParcelCancelledListenerTest {

    private final DeliveryAssignmentService service = mock(DeliveryAssignmentService.class);
    private final ParcelCancelledListener listener =
            new ParcelCancelledListener(service, JsonMapper.builder().build());

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void aValidCancellationStopsTheParcelsAssignments() {
        var parcelId = UUID.randomUUID();

        listener.receive(message("{\"eventType\":\"parcel.cancelled\",\"data\":{\"parcelId\":\"" + parcelId + "\"}}"));

        verify(service).cancelForParcel(parcelId);
    }

    @Test
    void unreadableOrWrongMessagesAreRejectedWithoutRequeue() {
        for (var body : new String[]{"not json", "{}", "{\"eventType\":\"parcel.booked\",\"data\":{\"parcelId\":\""
                + UUID.randomUUID() + "\"}}", "{\"eventType\":\"parcel.cancelled\",\"data\":{\"parcelId\":\"nope\"}}"}) {
            assertThatThrownBy(() -> listener.receive(message(body)))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verifyNoInteractions(service);
    }
}
