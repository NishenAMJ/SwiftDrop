package com.swiftdrop.tracking.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.tracking.tracking.TrackingQueryService;
import com.swiftdrop.tracking.tracking.TrackingState;

@ExtendWith(MockitoExtension.class)
class TrackingLiveControllerTests {

    @Mock
    private TrackingLiveUpdateService liveUpdates;

    @Mock
    private TrackingQueryService trackingQueries;

    @Mock
    private SseEmitter emitter;

    @Test
    void registersTheClientThenSendsItsCurrentSnapshot() {
        String trackingNumber = "SWD-2026-100008";
        TrackingState state = new TrackingState(
                trackingNumber,
                "IN_TRANSIT",
                "Western Route",
                Instant.parse("2026-11-14T10:20:00Z"));
        when(liveUpdates.subscribe(trackingNumber)).thenReturn(emitter);
        when(trackingQueries.getCurrentState(trackingNumber)).thenReturn(state);
        TrackingLiveController controller = new TrackingLiveController(liveUpdates, trackingQueries);

        SseEmitter result = controller.stream(trackingNumber);

        assertThat(result).isSameAs(emitter);
        InOrder flow = inOrder(trackingQueries, liveUpdates);
        flow.verify(trackingQueries).requireTrackingNumber(trackingNumber);
        flow.verify(liveUpdates).subscribe(trackingNumber);
        flow.verify(trackingQueries).getCurrentState(trackingNumber);
        flow.verify(liveUpdates).sendSnapshot(emitter, state);
    }
}
