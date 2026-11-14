package com.swiftdrop.tracking.live;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.tracking.tracking.TrackingState;

@ExtendWith(MockitoExtension.class)
class TrackingLiveUpdateServiceTests {

    @Mock
    private SseEmitterFactory emitterFactory;

    @Mock
    private SseEmitter emitter;

    private TrackingLiveUpdateService liveUpdates;

    @BeforeEach
    void setUp() {
        when(emitterFactory.create(anyLong())).thenReturn(emitter);
        liveUpdates = new TrackingLiveUpdateService(emitterFactory, Duration.ofMinutes(30));
    }

    @Test
    void sendsNewStateToSubscribedClients() throws IOException {
        String trackingNumber = "SWD-2026-100004";
        liveUpdates.subscribe(trackingNumber);

        liveUpdates.publish(new TrackingState(
                trackingNumber,
                "IN_TRANSIT",
                "Colombo Hub",
                Instant.parse("2026-11-14T10:05:00Z")));

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendsSnapshotAndHeartbeatAfterAClientReconnects() throws IOException {
        String trackingNumber = "SWD-2026-100005";
        TrackingState currentState = new TrackingState(
                trackingNumber,
                "OUT_FOR_DELIVERY",
                "Colombo Delivery Area",
                Instant.parse("2026-11-14T10:10:00Z"));
        SseEmitter subscribedEmitter = liveUpdates.subscribe(trackingNumber);

        liveUpdates.sendSnapshot(subscribedEmitter, currentState);
        liveUpdates.sendHeartbeats();

        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void removesAClientWhenItsEmitterCompletes() {
        String trackingNumber = "SWD-2026-100006";
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        liveUpdates.subscribe(trackingNumber);
        verify(emitter).onCompletion(completion.capture());
        assertThat(liveUpdates.activeClientCount(trackingNumber)).isEqualTo(1);

        completion.getValue().run();

        assertThat(liveUpdates.activeClientCount(trackingNumber)).isZero();
    }

    @Test
    void removesADisconnectedClientWhenSendingFails() throws IOException {
        String trackingNumber = "SWD-2026-100007";
        liveUpdates.subscribe(trackingNumber);
        doThrow(new IOException("client disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        liveUpdates.publish(new TrackingState(
                trackingNumber,
                "DELIVERED",
                "Destination Area",
                Instant.parse("2026-11-14T10:15:00Z")));

        assertThat(liveUpdates.activeClientCount(trackingNumber)).isZero();
        verify(emitter).complete();
    }
}
