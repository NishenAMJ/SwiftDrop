package com.swiftdrop.identity.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.ApiTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Suspended accounts, expired sessions and token types that must not unlock person-only actions. */
class SessionSecurityTests extends ApiTestSupport {

    private static final String MERCHANT_APPLICATION = """
        {"businessName": "Session Test Store", "businessType": "INDIVIDUAL", "contactPhone": "+94771234567",
         "businessAddress": {"line1": "1 Main Street", "city": "Galle"}}
        """;

    @Autowired
    private JdbcTemplate jdbc;

    private void suspend(String userId) {
        jdbc.update("UPDATE identity_merchant.users SET status = 'SUSPENDED' WHERE user_id = ?", UUID.fromString(userId));
    }

    @Test
    void suspendedUserCannotLogIn() throws Exception {
        String email = uniqueEmail();
        String userId = session(register(email, PASSWORD).andReturn()).userId();
        suspend(userId);

        login(email, PASSWORD)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void suspendingAUserEndsTheirSessionsOnTheNextRefresh() throws Exception {
        Session session = registerAndLogin();
        suspend(session.userId());

        refresh(session.refreshToken())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

        jdbc.update("UPDATE identity_merchant.users SET status = 'ACTIVE' WHERE user_id = ?", UUID.fromString(session.userId()));
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Session session = registerAndLogin();
        jdbc.update("UPDATE identity_merchant.refresh_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?",
            UUID.fromString(session.userId()));

        refresh(session.refreshToken())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void blankRefreshCookieIsRejected() throws Exception {
        refresh(" ").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithAnUnknownTokenStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE, "unknown-token")))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        Session session = registerAndLogin();
        String[] parts = session.accessToken().split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        getWithToken("/api/v1/users/me", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void merchantApiKeyTokenCannotChangeTheOwnersPassword() throws Exception {
        Session applicant = registerAndLogin();
        String dispatcher = activeDispatcherToken();
        String merchantId = JsonPath.read(postWithToken("/api/v1/merchants", applicant.accessToken(), MERCHANT_APPLICATION)
            .andReturn().getResponse().getContentAsString(), "$.id");
        postWithToken("/api/v1/merchants/" + merchantId + "/approve", dispatcher, null).andExpect(status().isOk());
        String merchantToken = session(refresh(applicant.refreshToken()).andReturn()).accessToken();
        String key = JsonPath.read(postWithToken("/api/v1/merchants/me/api-keys", merchantToken, "{\"name\": \"ERP\"}")
            .andReturn().getResponse().getContentAsString(), "$.key");
        String apiKeyToken = JsonPath.read(mockMvc.perform(post("/api/v1/auth/token").header("X-API-Key", key)
            .contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString(), "$.accessToken");

        postWithToken("/api/v1/users/me/password", apiKeyToken,
            "{\"currentPassword\": \"%s\", \"newPassword\": \"Another-pass-99\"}".formatted(PASSWORD))
            .andExpect(status().isForbidden());
        login(JsonPath.read(getWithToken("/api/v1/users/me", applicant.accessToken())
            .andReturn().getResponse().getContentAsString(), "$.email"), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unapprovedMerchantCannotCreateApiKeys() throws Exception {
        Session applicant = registerAndLogin();
        postWithToken("/api/v1/merchants", applicant.accessToken(), MERCHANT_APPLICATION).andExpect(status().isCreated());
        String pendingToken = session(refresh(applicant.refreshToken()).andReturn()).accessToken();

        postWithToken("/api/v1/merchants/me/api-keys", pendingToken, "{\"name\": \"Too early\"}")
            .andExpect(status().isForbidden());
    }

    @Test
    void merchantSearchTextIsBounded() throws Exception {
        String dispatcher = activeDispatcherToken();
        mockMvc.perform(get("/api/v1/merchants").param("q", "x".repeat(101))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dispatcher))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customerCannotListUsersOrCreateDispatchers() throws Exception {
        Session customer = registerAndLogin();
        getWithToken("/api/v1/users", customer.accessToken()).andExpect(status().isForbidden());
        postWithToken("/api/v1/dispatchers", customer.accessToken(),
            "{\"email\": \"%s\", \"fullName\": \"X\", \"initialPassword\": \"Initial-pass-1\"}".formatted(uniqueEmail()))
            .andExpect(status().isForbidden());
    }
}
