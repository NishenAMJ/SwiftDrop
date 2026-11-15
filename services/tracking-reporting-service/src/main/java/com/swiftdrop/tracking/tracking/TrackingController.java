package com.swiftdrop.tracking.tracking;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class TrackingController {

    private static final String TRACKING_NUMBER_PATTERN = "^SWD-[0-9]{4}-[0-9]{6,}$";

    private final TrackingQueryService trackingQueries;

    public TrackingController(TrackingQueryService trackingQueries) {
        this.trackingQueries = trackingQueries;
    }

    @GetMapping("/public/tracking/{trackingNumber}")
    public TrackingSummaryResponse publicSummary(
            @PathVariable @Pattern(regexp = TRACKING_NUMBER_PATTERN) String trackingNumber) {
        return trackingQueries.getSummary(trackingNumber);
    }

    @GetMapping("/public/tracking/{trackingNumber}/history")
    public TrackingHistoryResponse publicHistory(
            @PathVariable @Pattern(regexp = TRACKING_NUMBER_PATTERN) String trackingNumber) {
        return trackingQueries.getHistory(trackingNumber);
    }

    @GetMapping("/tracking/{trackingNumber}")
    public TrackingSummaryResponse authenticatedSummary(
            @PathVariable @Pattern(regexp = TRACKING_NUMBER_PATTERN) String trackingNumber) {
        return trackingQueries.getSummary(trackingNumber);
    }
}
