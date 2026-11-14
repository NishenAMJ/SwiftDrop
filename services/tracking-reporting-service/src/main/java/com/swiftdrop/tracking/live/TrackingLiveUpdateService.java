package com.swiftdrop.tracking.live;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.tracking.tracking.TrackingState;

@Service
public class TrackingLiveUpdateService {

    private final ConcurrentMap<String, Set<SseEmitter>> clients = new ConcurrentHashMap<>();
    private final SseEmitterFactory emitterFactory;
    private final long timeoutMillis;

    public TrackingLiveUpdateService(
            SseEmitterFactory emitterFactory,
            @Value("${tracking.sse.timeout:30m}") Duration timeout) {
        this.emitterFactory = emitterFactory;
        this.timeoutMillis = timeout.toMillis();
    }

    public SseEmitter subscribe(String trackingNumber) {
        SseEmitter emitter = emitterFactory.create(timeoutMillis);
        clients.computeIfAbsent(trackingNumber, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable remove = () -> remove(trackingNumber, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
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
            send(state.trackingNumber(), emitter, SseEmitter.event()
                    .id(state.updatedAt().toString())
                    .name("tracking-update")
                    .data(state));
        }
    }

    public void sendSnapshot(SseEmitter emitter, TrackingState state) {
        send(state.trackingNumber(), emitter, SseEmitter.event()
                .id(state.updatedAt().toString())
                .name("tracking-snapshot")
                .data(state));
    }

    @Scheduled(fixedDelayString = "${tracking.sse.heartbeat-interval:25s}")
    public void sendHeartbeats() {
        String heartbeatTime = Instant.now().toString();
        clients.forEach((trackingNumber, subscribers) -> {
            for (SseEmitter emitter : subscribers) {
                send(trackingNumber, emitter, SseEmitter.event()
                        .name("heartbeat")
                        .data(heartbeatTime));
            }
        });
    }

    int activeClientCount(String trackingNumber) {
        Set<SseEmitter> subscribers = clients.get(trackingNumber);
        return subscribers == null ? 0 : subscribers.size();
    }

    private void send(String trackingNumber, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            remove(trackingNumber, emitter);
            emitter.complete();
        }
    }

    private void remove(String trackingNumber, SseEmitter emitter) {
        clients.computeIfPresent(trackingNumber, (ignored, subscribers) -> {
            subscribers.remove(emitter);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
