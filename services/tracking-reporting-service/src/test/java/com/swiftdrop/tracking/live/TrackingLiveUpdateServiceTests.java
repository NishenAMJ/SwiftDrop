package com.swiftdrop.tracking.live;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
        liveUpdates = new TrackingLiveUpdateService(emitterFactory);
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
}
