package com.swiftdrop.tracking.report;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.tracking.event.EventEnvelope;
import com.swiftdrop.tracking.event.TrackingEventData;
import com.swiftdrop.tracking.tracking.TrackingProjectionUpdater;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Transactional
class ReportApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackingProjectionUpdater updater;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void createReportData() {
        // Keep report expectations independent of records already present in a
        // developer's local database. The surrounding test transaction restores
        // all existing rows after each test.
        entityManager.createNativeQuery("DELETE FROM tracking_reporting.tracking_history").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM tracking_reporting.tracking_records").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM tracking_reporting.processed_events").executeUpdate();

        Parcel delivered = bookParcel("2026-11-01T09:00:00Z");
        update(delivered, "delivery.delivered", "DELIVERED", "2026-11-03T09:00:00Z");

        Parcel failed = bookParcel("2026-11-02T09:00:00Z");
        update(failed, "delivery.failed", "DELIVERY_FAILED", "2026-11-04T09:00:00Z");

        Parcel inTransit = bookParcel("2026-11-02T10:00:00Z");
        update(inTransit, "delivery.in-transit", "IN_TRANSIT", "2026-11-04T10:00:00Z");

        Parcel outsideBookingCohort = bookParcel("2026-10-31T09:00:00Z");
        update(outsideBookingCohort, "delivery.delivered", "DELIVERED", "2026-11-03T11:00:00Z");

        entityManager.flush();
    }

    @Test
    void summaryUsesTheFilteredBookingCohort() throws Exception {
        mockMvc.perform(dispatcherGet("/api/v1/reports/summary")
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.endDate").value("2026-11-30"))
                .andExpect(jsonPath("$.totalParcelsBooked").value(3))
                .andExpect(jsonPath("$.parcelsCurrentlyInTransit").value(1))
                .andExpect(jsonPath("$.deliveredParcels").value(1))
                .andExpect(jsonPath("$.failedDeliveries").value(1))
                .andExpect(jsonPath("$.deliverySuccessRate").value(50.0));
    }

    @Test
    void statusBreakdownUsesCurrentStatusesForTheCohort() throws Exception {
        mockMvc.perform(dispatcherGet("/api/v1/reports/status-breakdown")
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses.length()").value(3))
                .andExpect(jsonPath("$.statuses[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.statuses[0].total").value(1))
                .andExpect(jsonPath("$.statuses[1].status").value("DELIVERY_FAILED"))
                .andExpect(jsonPath("$.statuses[1].total").value(1))
                .andExpect(jsonPath("$.statuses[2].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.statuses[2].total").value(1));
    }

    @Test
    void dailyDeliveriesCountDistinctDeliveredParcelsByUtcDay() throws Exception {
        mockMvc.perform(dispatcherGet("/api/v1/reports/deliveries/daily")
                        .param("startDate", "2026-11-03")
                        .param("endDate", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.length()").value(1))
                .andExpect(jsonPath("$.counts[0].period").value("2026-11-03"))
                .andExpect(jsonPath("$.counts[0].deliveredParcels").value(2));
    }

    @Test
    void monthlyDeliveriesCountDistinctDeliveredParcelsByUtcMonth() throws Exception {
        mockMvc.perform(dispatcherGet("/api/v1/reports/deliveries/monthly")
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.length()").value(1))
                .andExpect(jsonPath("$.counts[0].period").value("2026-11-01"))
                .andExpect(jsonPath("$.counts[0].deliveredParcels").value(2));
    }

    @Test
    void invalidDateRangeReturnsValidationProblem() throws Exception {
        mockMvc.perform(dispatcherGet("/api/v1/reports/summary")
                        .param("startDate", "2026-11-30")
                        .param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("startDate must not be after endDate."));
    }

    @Test
    void reportsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void reportsRejectAuthenticatedNonDispatchers() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dispatcherGet(String path) {
        return get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")));
    }

    private Parcel bookParcel(String occurredAt) {
        UUID parcelId = UUID.randomUUID();
        String trackingNumber = "SWD-2026-" + String.format(
                "%06d", Math.floorMod(parcelId.hashCode(), 1_000_000));
        Parcel parcel = new Parcel(parcelId, trackingNumber);
        updater.process(event(parcel, "parcel.booked", "BOOKED", occurredAt));
        return parcel;
    }

    private void update(Parcel parcel, String eventType, String status, String occurredAt) {
        updater.process(event(parcel, eventType, status, occurredAt));
    }

    private EventEnvelope event(Parcel parcel, String eventType, String status, String occurredAt) {
        TrackingEventData data = new TrackingEventData(
                parcel.parcelId(),
                parcel.trackingNumber(),
                status,
                "Public tracking update",
                "Safe Area",
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
                Instant.parse(occurredAt),
                eventType.equals("parcel.booked")
                        ? "parcel-pricing-service"
                        : "delivery-operations-service",
                data);
    }

    private record Parcel(UUID parcelId, String trackingNumber) {
    }
}
