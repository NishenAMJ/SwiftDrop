package com.swiftdrop.tracking.live;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TrackingLiveEventListener {

    private final TrackingLiveUpdateService liveUpdates;

    public TrackingLiveEventListener(TrackingLiveUpdateService liveUpdates) {
        this.liveUpdates = liveUpdates;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrackingStateChanged(TrackingStateChangedEvent event) {
        liveUpdates.publish(event.state());
    }
}
