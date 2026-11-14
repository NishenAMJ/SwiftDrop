package com.swiftdrop.tracking.live;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingLiveController {

    private final TrackingLiveUpdateService liveUpdates;

    public TrackingLiveController(TrackingLiveUpdateService liveUpdates) {
        this.liveUpdates = liveUpdates;
    }

    @GetMapping(path = "/{trackingNumber}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable
            @Pattern(regexp = "^SWD-[0-9]{4}-[0-9]{6,}$") String trackingNumber) {
        return liveUpdates.subscribe(trackingNumber);
    }
}
