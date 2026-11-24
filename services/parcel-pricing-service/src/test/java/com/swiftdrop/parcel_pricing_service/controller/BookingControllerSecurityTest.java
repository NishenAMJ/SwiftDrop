package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.config.SecurityConfig;
import com.swiftdrop.parcel_pricing_service.service.BookingCancellationService;
import com.swiftdrop.parcel_pricing_service.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security rules and error mapping for the customer booking endpoints. */
@WebMvcTest(controllers = {BookingController.class, BookingCancellationController.class},
        properties = {"spring.config.import=", "debug=false"})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class BookingControllerSecurityTest {

    private static final String VALID_BOOKING = """
            {"weightKg":2,"urgency":"NORMAL","paymentMethod":"COD",
             "pickupAddress":{"contactName":"Nimal","contactPhone":"0771234567","streetAddress":"12 Main St","city":"Colombo"},
             "deliveryAddress":{"contactName":"Kamala","contactPhone":"0777654321","streetAddress":"45 Lake Rd","city":"Kandy","zoneCode":"KANDY"}}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean BookingService bookingService;
    @MockitoBean BookingCancellationService cancellationService;
    @MockitoBean BookingPaymentVerifier paymentVerifier;
    @MockitoBean JwtDecoder decoder;

    private final UUID customer = UUID.randomUUID();

    private RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(customer.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void everyBookingEndpointNeedsALogin() throws Exception {
        mvc.perform(get("/api/v1/bookings/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/track/SWD-2026-000001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customer/profile")).andExpect(status().isUnauthorized());
        verifyNoInteractions(bookingService);
    }

    @Test
    void customerCanCreateABooking() throws Exception {
        mvc.perform(post("/api/v1/bookings").with(as("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isCreated());
    }

    @Test
    void dispatcherOnlyAccountCannotCreateACustomerBooking() throws Exception {
        mvc.perform(post("/api/v1/bookings").with(as("DISPATCHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOKING))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(bookingService);
    }

    @Test
    void invalidBookingFieldsAreReportedPerField() throws Exception {
        String bad = VALID_BOOKING.replace("\"NORMAL\"", "\"FAST\"").replace("0777654321", "call me");
        mvc.perform(post("/api/v1/bookings").with(as("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.urgency").exists())
                .andExpect(jsonPath("$.fieldErrors['deliveryAddress.contactPhone']").exists());
    }

    @Test
    void malformedJsonAndBadIdsAre400Not500() throws Exception {
        mvc.perform(post("/api/v1/bookings").with(as("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get("/api/v1/bookings/not-a-uuid").with(as("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void paymentContextErrorsKeepTheirRealStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getPaymentContext(eq(id), any())).thenThrow(ApiException.forbidden("You do not own this booking."));
        mvc.perform(get("/api/v1/bookings/" + id + "/payment-context").with(as("CUSTOMER")))
                .andExpect(status().isForbidden());

        UUID other = UUID.randomUUID();
        when(bookingService.getPaymentContext(eq(other), any())).thenThrow(ApiException.notFound("Booking was not found."));
        mvc.perform(get("/api/v1/bookings/" + other + "/payment-context").with(as("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalDetails() throws Exception {
        when(bookingService.getCustomerBookings(any())).thenThrow(new IllegalStateException("SQL password=secret"));
        mvc.perform(get("/api/v1/bookings/my").with(as("CUSTOMER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again later."));
    }

    @Test
    void trackingSomeoneElsesBookingIsNotFound() throws Exception {
        when(bookingService.getOwnedBookingByTrackingNumber(eq("SWD-2026-000009"), eq(customer)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No booking of yours has this tracking number."));
        mvc.perform(get("/api/v1/bookings/track/SWD-2026-000009").with(as("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmPaymentNeedsAPaymentId() throws Exception {
        mvc.perform(post("/api/v1/bookings/" + UUID.randomUUID() + "/confirm-payment").with(as("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"));
        verifyNoInteractions(paymentVerifier);
    }
}
