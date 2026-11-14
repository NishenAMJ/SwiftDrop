package com.swiftdrop.tracking.live;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.tracking.tracking.TrackingState;

@Service
public class TrackingLiveUpdateService {

    private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1_000L;

    private final ConcurrentMap<String, Set<SseEmitter>> clients = new ConcurrentHashMap<>();
    private final SseEmitterFactory emitterFactory;

    public TrackingLiveUpdateService(SseEmitterFactory emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter subscribe(String trackingNumber) {
        SseEmitter emitter = emitterFactory.create(SSE_TIMEOUT_MILLIS);
        clients.computeIfAbsent(trackingNumber, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable remove = () -> remove(trackingNumber, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data(trackingNumber));
        } catch (IOException exception) {
            remove.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(TrackingState state) {
        Set<SseEmitter> subscribers = clients.get(state.trackingNumber());
        if (subscribers == null) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .id(state.updatedAt().toString())
                        .name("tracking-update")
                        .data(state));
            } catch (IOException | IllegalStateException exception) {
                remove(state.trackingNumber(), emitter);
                emitter.complete();
            }
        }
    }

    private void remove(String trackingNumber, SseEmitter emitter) {
        clients.computeIfPresent(trackingNumber, (ignored, subscribers) -> {
            subscribers.remove(emitter);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
