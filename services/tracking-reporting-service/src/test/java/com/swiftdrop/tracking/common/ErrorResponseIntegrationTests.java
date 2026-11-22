package com.swiftdrop.tracking.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Problem-details responses: every error has a stable code and never leaks internal details. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class ErrorResponseIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpServletRequestBuilder asDispatcher(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")));
    }

    @Test
    void reportWithStartAfterEndIsAValidationProblem() throws Exception {
        mockMvc.perform(asDispatcher(get("/api/v1/reports/summary"))
                        .param("startDate", "2026-11-30")
                        .param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedReportDateIsAValidationProblemWithACode() throws Exception {
        mockMvc.perform(asDispatcher(get("/api/v1/reports/summary")).param("startDate", "2026-13-45"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("DateTimeParseException"))));
    }

    @Test
    void unsupportedMethodGetsAMethodNotAllowedCode() throws Exception {
        mockMvc.perform(asDispatcher(post("/api/v1/reports/summary")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownTrackingNumberForAnAuthenticatedCallerIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", "SWD-2026-000404").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void malformedBearerTokenIsRejectedAsAnInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", "SWD-2026-000404")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingTokenAsksForAuthenticationWithoutClaimingTheTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", "SWD-2026-000404"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, not(containsString("invalid_token"))));
    }

    @Test
    void nonDispatcherCannotReadReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/status-breakdown")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
