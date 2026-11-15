package com.swiftdrop.tracking.tracking;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.tracking.event.EventEnvelope;
import com.swiftdrop.tracking.event.TrackingEventData;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Transactional
class TrackingApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackingProjectionUpdater updater;

    private UUID parcelId;
    private String trackingNumber;

    @BeforeEach
    void createTrackingRecord() {
        parcelId = UUID.randomUUID();
        trackingNumber = "SWD-2026-" + String.format(
                "%06d", Math.floorMod(parcelId.hashCode(), 1_000_000));
        updater.process(event(
                "parcel.booked",
                "BOOKED",
                "Parcel booking confirmed",
                "Colombo Hub",
                Instant.parse("2026-11-14T10:00:00Z")));
    }

    @Test
    void publicSummaryIsAnonymousAndContainsOnlySafeTrackingFields() throws Exception {
        mockMvc.perform(get("/api/v1/public/tracking/{trackingNumber}", trackingNumber))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.currentStatus").value("BOOKED"))
                .andExpect(jsonPath("$.publicDescription").value("Parcel booking confirmed"))
                .andExpect(jsonPath("$.safeLocation").value("Colombo Hub"))
                .andExpect(jsonPath("$.latestUpdateTime").value("2026-11-14T10:00:00Z"))
                .andExpect(jsonPath("$.parcelId").doesNotExist())
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.courierId").doesNotExist())
                .andExpect(jsonPath("$.proof").doesNotExist())
                .andExpect(jsonPath("$.internalNotes").doesNotExist());
    }

    @Test
    void publicHistoryIsOrderedByEventTime() throws Exception {
        updater.process(event(
                "delivery.in-transit",
                "IN_TRANSIT",
                "Parcel is moving to the destination area",
                "Western Route",
                Instant.parse("2026-11-14T11:00:00Z")));

        mockMvc.perform(get("/api/v1/public/tracking/{trackingNumber}/history", trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.events[0].eventTime").value("2026-11-14T10:00:00Z"))
                .andExpect(jsonPath("$.events[1].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.events[1].eventTime").value("2026-11-14T11:00:00Z"));
    }

    @Test
    void unknownTrackingNumberReturnsSafeProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/public/tracking/SWD-2026-999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Tracking information was not found."))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void authenticatedSummaryRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", trackingNumber))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void authenticatedSummaryAcceptsAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", trackingNumber).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber));
    }

    @Test
    void liveStreamRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/stream", trackingNumber))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void liveStreamReturnsNotFoundBeforeOpeningAnUnknownStream() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/SWD-2026-999999/stream").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void malformedTrackingNumberReturnsValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/public/tracking/not-a-tracking-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private EventEnvelope event(
            String eventType,
            String status,
            String description,
            String location,
            Instant occurredAt) {
        TrackingEventData data = new TrackingEventData(
                parcelId,
                trackingNumber,
                status,
                description,
                location,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                eventType.startsWith("parcel.") ? "parcel-pricing-service" : "delivery-operations-service",
                data);
    }
}
