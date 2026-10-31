package com.swiftdrop.identity.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.ApiTestSupport;

class ApiKeyFlowTests extends ApiTestSupport {

    private static final String APPLICATION = """
        {"businessName": "Key Test Store", "businessType": "INDIVIDUAL", "contactPhone": "+94771234567",
         "businessAddress": {"line1": "1 Main Street", "city": "Galle"}}
        """;

    private String dispatcher;
    private String merchantId;
    private String merchantToken;

    @BeforeEach
    void approvedMerchant() throws Exception {
        Session applicant = registerAndLogin();
        dispatcher = activeDispatcherToken();
        merchantId = JsonPath.read(postWithToken("/api/v1/merchants", applicant.accessToken(), APPLICATION)
            .andReturn().getResponse().getContentAsString(), "$.id");
        postWithToken("/api/v1/merchants/" + merchantId + "/approve", dispatcher, null).andExpect(status().isOk());
        merchantToken = session(refresh(applicant.refreshToken()).andReturn()).accessToken();
    }

    @Test
    void keyIsShownOnceAndExchangesForAMerchantToken() throws Exception {
        String body = createKey("{\"name\": \"Shopify store\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key", startsWith("sd_live_")))
            .andExpect(jsonPath("$.prefix", startsWith("sd_live_")))
            .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.key");
        String prefix = JsonPath.read(body, "$.prefix");
        assertThat(key).startsWith(prefix).hasSize("sd_live_".length() + 32);

        getWithToken("/api/v1/merchants/me/api-keys", merchantToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Shopify store"))
            .andExpect(jsonPath("$[0].prefix").value(prefix))
            .andExpect(jsonPath("$[0].key").doesNotExist());

        String token = JsonPath.read(exchange(key)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user").doesNotExist())
            .andReturn().getResponse().getContentAsString(), "$.accessToken");
        DocumentContext claims = claims(token);
        assertThat((List<String>) claims.read("$.roles")).containsExactly("MERCHANT");
        assertThat((String) claims.read("$.merchant_id")).isEqualTo(merchantId);
        assertThat((String) claims.read("$.auth")).isEqualTo("api_key");

        getWithToken("/api/v1/merchants/me/api-keys", merchantToken)
            .andExpect(jsonPath("$[0].lastUsedAt").exists());
    }

    @Test
    void apiKeyTokensCanOnlyReadTheMerchantRecord() throws Exception {
        String key = JsonPath.read(createKey("{\"name\": \"ERP\"}").andReturn().getResponse().getContentAsString(), "$.key");
        String token = JsonPath.read(exchange(key).andReturn().getResponse().getContentAsString(), "$.accessToken");

        getWithToken("/api/v1/merchants/me", token).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(merchantId));

        // A leaked key must not be able to create more keys or touch the owner's account.
        getWithToken("/api/v1/merchants/me/api-keys", token).andExpect(status().isForbidden());
        postWithToken("/api/v1/merchants/me/api-keys", token, "{\"name\": \"backdoor\"}").andExpect(status().isForbidden());
        getWithToken("/api/v1/users/me", token).andExpect(status().isForbidden());
        postWithToken("/api/v1/users/me/password", token, "{\"newPassword\": \"Takeover-123\"}")
            .andExpect(status().isForbidden());
    }

    @Test
    void revokedSuspendedAndUnknownKeysAreRefused() throws Exception {
        String body = createKey("{\"name\": \"Old\"}").andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.key");
        String keyId = JsonPath.read(body, "$.id");

        deleteWithToken("/api/v1/merchants/me/api-keys/" + keyId, merchantToken).andExpect(status().isNoContent());
        deleteWithToken("/api/v1/merchants/me/api-keys/" + keyId, merchantToken).andExpect(status().isNoContent());
        exchange(key).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
        getWithToken("/api/v1/merchants/me/api-keys", merchantToken).andExpect(jsonPath("$[0].revokedAt").exists());

        String live = JsonPath.read(createKey("{\"name\": \"Live\"}").andReturn().getResponse().getContentAsString(), "$.key");
        exchange(live).andExpect(status().isOk());
        postWithToken("/api/v1/merchants/" + merchantId + "/suspend", dispatcher, "{\"reason\": \"Chargebacks\"}")
            .andExpect(status().isOk());
        exchange(live).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_ACTIVE"));

        exchange("sd_live_" + "x".repeat(32)).andExpect(status().isUnauthorized());
        exchange("not-a-key").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/token")).andExpect(status().isUnauthorized());
    }

    @Test
    void atMostFiveActiveKeys() throws Exception {
        for (int i = 0; i < 5; i++) {
            createKey("{\"name\": \"Key " + i + "\"}").andExpect(status().isCreated());
        }
        createKey("{\"name\": \"Key 6\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("API_KEY_LIMIT_REACHED"));

        String firstId = JsonPath.read(getWithToken("/api/v1/merchants/me/api-keys", merchantToken)
            .andReturn().getResponse().getContentAsString(), "$[0].id");
        deleteWithToken("/api/v1/merchants/me/api-keys/" + firstId, merchantToken).andExpect(status().isNoContent());
        createKey("{\"name\": \"Key 6\"}").andExpect(status().isCreated());
    }

    @Test
    void keysAreValidatedAndScopedToTheirMerchant() throws Exception {
        createKey("{\"name\": \"\"}").andExpect(status().isBadRequest());
        createKey("{\"name\": \"Past\", \"expiresAt\": \"2020-01-01T00:00:00Z\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("expiresAt"));

        String keyId = JsonPath.read(createKey("{\"name\": \"Mine\"}").andReturn().getResponse().getContentAsString(), "$.id");
        String customer = registerAndLogin().accessToken();
        getWithToken("/api/v1/merchants/me/api-keys", customer).andExpect(status().isForbidden());
        deleteWithToken("/api/v1/merchants/me/api-keys/" + keyId, customer).andExpect(status().isForbidden());
    }

    private ResultActions createKey(String json) throws Exception {
        return postWithToken("/api/v1/merchants/me/api-keys", merchantToken, json);
    }

    private ResultActions exchange(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/token").header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON));
    }
}
