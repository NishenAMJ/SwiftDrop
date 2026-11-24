package com.swiftdrop.communication.webhook;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.swiftdrop.communication.support.AbstractIntegrationTest;

import tools.jackson.databind.json.JsonMapper;

/**
 * Authorization rules of the webhook API: who may call what, and that a merchant can never see
 * or change another merchant's webhooks.
 */
class WebhookAuthorizationTest extends AbstractIntegrationTest {

    private static final String SUBSCRIPTIONS = "/api/v1/webhooks/subscriptions";
    private static final String VALID_BODY = """
            { "url": "http://localhost:9/hook", "eventTypes": ["parcel.status-changed"] }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    // ---- authentication and roles -------------------------------------------------------

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mockMvc.perform(get(SUBSCRIPTIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SUBSCRIPTIONS).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/webhooks/deliveries").param("status", "FAILED"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerAndCourierGet403() throws Exception {
        for (String role : new String[] {"ROLE_CUSTOMER", "ROLE_COURIER"}) {
            mockMvc.perform(get(SUBSCRIPTIONS).with(withRole(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(post(SUBSCRIPTIONS).with(withRole(role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void merchantWithoutMerchantIdClaimGets403() throws Exception {
        mockMvc.perform(get(SUBSCRIPTIONS).with(withRole("ROLE_MERCHANT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
    }

    @Test
    void onlyDispatcherCanListFailedDeliveries() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/deliveries").param("status", "FAILED").with(withRole("ROLE_DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/webhooks/deliveries").param("status", "FAILED").with(merchant(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    // ---- ownership ----------------------------------------------------------------------

    @Test
    void merchantCannotTouchAnotherMerchantsSubscription() throws Exception {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        String id = createSubscription(merchantA);
        String one = SUBSCRIPTIONS + "/" + id;

        // Merchant B: every operation answers 404, so B cannot even tell that it exists.
        mockMvc.perform(get(one).with(merchant(merchantB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
        mockMvc.perform(patch(one).with(merchant(merchantB))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"active\": false }"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(one + "/rotate-secret").with(merchant(merchantB))).andExpect(status().isNotFound());
        mockMvc.perform(post(one + "/test").with(merchant(merchantB))).andExpect(status().isNotFound());
        mockMvc.perform(get(one + "/deliveries").with(merchant(merchantB))).andExpect(status().isNotFound());
        mockMvc.perform(delete(one).with(merchant(merchantB))).andExpect(status().isNotFound());

        // Merchant B's own list does not include it.
        mockMvc.perform(get(SUBSCRIPTIONS).with(merchant(merchantB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        // Merchant A can do all of these on its own subscription.
        mockMvc.perform(get(one).with(merchant(merchantA))).andExpect(status().isOk());
        mockMvc.perform(patch(one).with(merchant(merchantA))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"active\": true }"))
                .andExpect(status().isOk());
        mockMvc.perform(post(one + "/test").with(merchant(merchantA))).andExpect(status().isAccepted());
        mockMvc.perform(get(one + "/deliveries").with(merchant(merchantA))).andExpect(status().isOk());
        mockMvc.perform(post(one + "/rotate-secret").with(merchant(merchantA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists());
        mockMvc.perform(delete(one).with(merchant(merchantA))).andExpect(status().isNoContent());
    }

    @Test
    void merchantIdInRequestBodyIsIgnored() throws Exception {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();

        // Merchant A tries to create a subscription "for" merchant B.
        String response = mockMvc.perform(post(SUBSCRIPTIONS).with(merchant(merchantA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "http://localhost:9/hook", "eventTypes": ["parcel.status-changed"],
                                  "merchantId": "%s" }
                                """.formatted(merchantB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").value(merchantA.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(response).get("id").asText();

        mockMvc.perform(get(SUBSCRIPTIONS + "/" + id).with(merchant(merchantB))).andExpect(status().isNotFound());
    }

    @Test
    void secretIsOnlyReturnedOnCreateAndRotate() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String id = createSubscription(merchantId);

        mockMvc.perform(get(SUBSCRIPTIONS + "/" + id).with(merchant(merchantId)))
                .andExpect(jsonPath("$.secret").doesNotExist());
        mockMvc.perform(get(SUBSCRIPTIONS).with(merchant(merchantId)))
                .andExpect(jsonPath("$.content[0].secret").doesNotExist());
        mockMvc.perform(patch(SUBSCRIPTIONS + "/" + id).with(merchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"active\": true }"))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    // ---- validation ---------------------------------------------------------------------

    @Test
    void invalidUrlIsRejectedWith400() throws Exception {
        mockMvc.perform(post(SUBSCRIPTIONS).with(merchant(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"url\": \"not-a-url\", \"eventTypes\": [\"parcel.status-changed\"] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void emptyEventTypesIsRejectedWith400AndFieldError() throws Exception {
        mockMvc.perform(post(SUBSCRIPTIONS).with(merchant(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"url\": \"http://localhost:9/hook\", \"eventTypes\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("eventTypes"));
    }

    // ---- helpers ------------------------------------------------------------------------

    private String createSubscription(UUID merchantId) throws Exception {
        String response = mockMvc.perform(post(SUBSCRIPTIONS).with(merchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").exists())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asText();
    }

    private static RequestPostProcessor withRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority(role));
    }

    private static RequestPostProcessor merchant(UUID merchantId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                .jwt(token -> token.claim("merchant_id", merchantId.toString()));
    }
}
