package com.swiftdrop.tracking.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointAllowsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void publicTrackingRuleDoesNotAllowAdditionalNestedPaths() throws Exception {
        mockMvc.perform(get("/api/v1/public/tracking/SWD-2026-100001/history/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void publicTrackingRuleAllowsOnlyGetRequests() throws Exception {
        mockMvc.perform(post("/api/v1/public/tracking/SWD-2026-100001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void internalTrackingRequiresAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/SWD-2026-100001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void reportsRequireDispatcherRole() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/reports/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"))))
                .andExpect(status().isOk());
    }

    @Test
    void unspecifiedEndpointsAreProtectedByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/administration/example"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
