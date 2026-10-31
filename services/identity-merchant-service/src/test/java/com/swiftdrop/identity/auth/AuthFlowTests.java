package com.swiftdrop.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.ApiTestSupport;
import com.swiftdrop.identity.event.OutboxEvent;
import com.swiftdrop.identity.event.OutboxEventRepository;

class AuthFlowTests extends ApiTestSupport {

    @Autowired
    private OutboxEventRepository outbox;

    @Test
    void registerCreatesCustomerAndReturnsTokens() throws Exception {
        String email = uniqueEmail();
        MvcResult result = register(email, PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.roles").value(contains("CUSTOMER")))
            .andExpect(jsonPath("$.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.user.authProviders").value(contains("PASSWORD")))
            .andExpect(jsonPath("$.user.merchantId").doesNotExist())
            .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
            .andExpect(cookie().secure(REFRESH_COOKIE, true))
            .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"))
            .andExpect(cookie().sameSite(REFRESH_COOKIE, "Strict"))
            .andReturn();

        Session session = session(result);
        DocumentContext claims = claims(session.accessToken());
        assertThat((String) claims.read("$.iss")).isEqualTo("swiftdrop-identity");
        assertThat((Object) claims.read("$.aud")).isEqualTo("swiftdrop-api");
        assertThat((String) claims.read("$.sub")).isEqualTo(session.userId());
        assertThat((List<String>) claims.read("$.roles")).containsExactly("CUSTOMER");
        assertThat((String) claims.read("$.auth")).isEqualTo("password");
        assertThat(((Number) claims.read("$.exp")).longValue() - ((Number) claims.read("$.iat")).longValue())
            .isEqualTo(900);
        assertThat((Map<String, Object>) claims.read("$")).doesNotContainKeys("email", "name", "phone_number");

        DocumentContext jwtHeader = jwtHeader(session.accessToken());
        assertThat((String) jwtHeader.read("$.alg")).isEqualTo("RS256");
        mockMvc.perform(get("/.well-known/jwks.json"))
            .andExpect(jsonPath("$.keys[0].kid").value((String) jwtHeader.read("$.kid")));
    }

    @Test
    void registerPublishesUserRegisteredEvent() throws Exception {
        String email = uniqueEmail();
        String userId = session(register(email, PASSWORD).andReturn()).userId();

        DocumentContext event = findEvent("user.registered", userId);
        assertThat((String) event.read("$.producer")).isEqualTo("identity-merchant-service");
        assertThat((Integer) event.read("$.version")).isEqualTo(1);
        assertThat((String) event.read("$.data.email")).isEqualTo(email);
        assertThat((String) event.read("$.data.authProvider")).isEqualTo("PASSWORD");
        assertThat((List<String>) event.read("$.data.roles")).containsExactly("CUSTOMER");
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
            .andExpect(status().isConflict())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesFieldsAndPasswordRules() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "not-an-email", "password": "x", "fullName": "", "phoneNumber": "0771234567"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.type").value("https://swiftdrop.dev/problems/validation-failed"))
            .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
            .andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
            .andExpect(jsonPath("$.errors[*].field", hasItem("phoneNumber")));

        register(uniqueEmail(), "onlyletters")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("password"))
            .andExpect(jsonPath("$.errors[0].message").value("must contain at least one letter and one digit"));
    }

    @Test
    void loginGivesSameErrorForUnknownEmailAndWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login(email, PASSWORD).andExpect(status().isOk()).andExpect(cookie().exists(REFRESH_COOKIE));
        login(email.toUpperCase(), PASSWORD).andExpect(status().isOk());

        String wrongPassword = login(email, "Wrong-password-1").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn().getResponse().getContentAsString();
        String unknownEmail = login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword.replace("/api/v1/auth/login", ""))
            .isEqualTo(unknownEmail.replace("/api/v1/auth/login", ""));
    }

    @Test
    void accessTokenAuthenticatesAndBadTokensAreRejected() throws Exception {
        String email = uniqueEmail();
        Session session = session(register(email, PASSWORD).andReturn());

        getWithToken("/api/v1/users/me", session.accessToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.id").value(session.userId()));

        String tampered = session.accessToken().substring(0, session.accessToken().length() - 4) + "AAAA";
        getWithToken("/api/v1/users/me", tampered)
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refreshRotatesTheCookie() throws Exception {
        Session session = registerAndLogin();

        Session refreshed = session(refresh(session.refreshToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(session.userId()))
            .andReturn());
        assertThat(refreshed.refreshToken()).isNotEqualTo(session.refreshToken());
        assertThat(refreshed.accessToken()).isNotEqualTo(session.accessToken());

        refresh(refreshed.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutOrWithUnknownCookieFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        Session session = registerAndLogin();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH_COOKIE, session.refreshToken())))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void updateMeChangesProfileAndPublishesEvent() throws Exception {
        Session session = registerAndLogin();

        mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"  New Name  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("New Name"))
            .andExpect(jsonPath("$.phoneNumber").value("+94771234567"));

        assertThat((String) findEvent("user.updated", session.userId()).read("$.data.fullName")).isEqualTo("New Name");

        mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].message").value("send at least one field to change"));
    }

    @Test
    void changePasswordLogsOutOtherDevicesButKeepsThisOne() throws Exception {
        String email = uniqueEmail();
        Session device1 = session(register(email, PASSWORD).andReturn());
        Session device2 = session(login(email, PASSWORD).andReturn());

        changePassword(device1.accessToken(), "Wrong-password-1", "New-password-99")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String newCookie = changePassword(device1.accessToken(), PASSWORD, "New-password-99")
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists(REFRESH_COOKIE))
            .andReturn().getResponse().getCookie(REFRESH_COOKIE).getValue();

        refresh(device2.refreshToken()).andExpect(status().isUnauthorized());
        refresh(device1.refreshToken()).andExpect(status().isUnauthorized());
        refresh(newCookie).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "New-password-99").andExpect(status().isOk());
    }

    @Test
    void jwksIsPublicAndHasNoPrivateParts() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].use").value("sig"))
            .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
            .andExpect(jsonPath("$.keys[0].n").exists())
            .andExpect(jsonPath("$.keys[0].d").doesNotExist())
            .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String accessToken, String current,
            String next) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next)));
    }

    private DocumentContext findEvent(String eventType, String userId) {
        return outbox.findAll().stream()
            .filter(e -> e.getEventType().equals(eventType))
            .map(OutboxEvent::getPayload)
            .map(JsonPath::parse)
            .filter(json -> userId.equals(json.read("$.data.userId")))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("No " + eventType + " event for user " + userId));
    }
}
