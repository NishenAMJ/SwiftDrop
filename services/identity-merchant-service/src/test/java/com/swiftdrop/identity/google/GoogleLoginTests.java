package com.swiftdrop.identity.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.ApiTestSupport;
import com.swiftdrop.identity.event.OutboxEventRepository;

/**
 * Google login with fake client credentials. Google itself isn't called: the start of the flow is
 * checked up to the redirect, and the end is checked by handing the handler what Spring Security
 * produces after a successful Google callback.
 */
@TestPropertySource(properties = {
    "swiftdrop.identity.google.client-id=test-client-id",
    "swiftdrop.identity.google.client-secret=test-client-secret",
    "swiftdrop.identity.frontend-base-url=http://localhost:4200",
})
class GoogleLoginTests extends ApiTestSupport {

    private static final String CALLBACK = "http://localhost:4200/auth/callback";

    @Autowired
    private GoogleLoginHandler handler;

    @Autowired
    private OutboxEventRepository outbox;

    @Test
    void startRedirectsToGoogleWithOurClientAndCallback() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://accounts.google.com/o/oauth2/v2/auth")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-client-id")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("login/oauth2/code/google")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("scope=openid")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("state=")));
    }

    @Test
    void newGoogleUserBecomesACustomerWithoutPassword() throws Exception {
        String email = uniqueEmail();
        MockHttpServletResponse response = finishGoogleLogin(googleUser("g-" + UUID.randomUUID(), email, true, "Kamala Silva"));

        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK);
        Cookie cookie = response.getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();

        // The frontend's next step: exchange the cookie for an access token.
        MvcResult refreshed = refresh(cookie.getValue())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.fullName").value("Kamala Silva"))
            .andExpect(jsonPath("$.user.authProviders[0]").value("GOOGLE"))
            .andExpect(jsonPath("$.user.authProviders.length()").value(1))
            .andReturn();
        Session session = session(refreshed);
        assertThat((String) claims(session.accessToken()).read("$.auth")).isEqualTo("google");
        assertThat((List<String>) claims(session.accessToken()).read("$.roles")).containsExactly("CUSTOMER");

        assertThat(outbox.findAll().stream()
            .filter(e -> e.getEventType().equals("user.registered"))
            .map(e -> JsonPath.parse(e.getPayload()))
            .filter(json -> session.userId().equals(json.read("$.data.userId")))
            .map(json -> (String) json.read("$.data.authProvider"))
            .toList()).containsExactly("GOOGLE");
    }

    @Test
    void sameGoogleAccountAlwaysReachesTheSameUser() throws Exception {
        String subject = "g-" + UUID.randomUUID();
        String first = userIdAfter(finishGoogleLogin(googleUser(subject, uniqueEmail(), true, "Same Person")));
        // Even if the Google account's email changed in the meantime.
        String second = userIdAfter(finishGoogleLogin(googleUser(subject, uniqueEmail(), true, "Same Person")));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void verifiedEmailIsLinkedToTheExistingPasswordAccount() throws Exception {
        String email = uniqueEmail();
        String userId = session(register(email, PASSWORD).andReturn()).userId();

        MockHttpServletResponse response = finishGoogleLogin(googleUser("g-" + UUID.randomUUID(), email.toUpperCase(), true, "Other Name"));

        refresh(response.getCookie(REFRESH_COOKIE).getValue())
            .andExpect(jsonPath("$.user.id").value(userId))
            .andExpect(jsonPath("$.user.fullName").value("Test User"))
            .andExpect(jsonPath("$.user.authProviders[0]").value("PASSWORD"))
            .andExpect(jsonPath("$.user.authProviders[1]").value("GOOGLE"));
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unverifiedEmailIsRefusedAndCreatesNothing() throws Exception {
        String email = uniqueEmail();
        MockHttpServletResponse response = finishGoogleLogin(googleUser("g-" + UUID.randomUUID(), email, false, "Unverified"));

        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK + "?error=email_not_verified");
        assertThat(response.getCookie(REFRESH_COOKIE)).isNull();
        assertThat(userService.emailExists(email)).isFalse();
    }

    @Test
    void anEmailLinkedToAnotherGoogleAccountIsRefused() throws Exception {
        String email = uniqueEmail();
        finishGoogleLogin(googleUser("g-" + UUID.randomUUID(), email, true, "Owner"));

        MockHttpServletResponse response = finishGoogleLogin(googleUser("g-" + UUID.randomUUID(), email, true, "Intruder"));

        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK + "?error=account_mismatch");
        assertThat(response.getCookie(REFRESH_COOKIE)).isNull();
    }

    @Test
    void cancelledOrFailedLoginGoesBackWithAnError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException("access_denied"));
        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK + "?error=google_login_failed");
    }

    @Test
    void googleOnlyUserCanSetAFirstPassword() throws Exception {
        String email = uniqueEmail();
        String cookie = finishGoogleLogin(googleUser("g-" + UUID.randomUUID(), email, true, "No Password")).getCookie(REFRESH_COOKIE).getValue();
        String token = session(refresh(cookie).andReturn()).accessToken();

        postWithToken("/api/v1/users/me/password", token, "{\"newPassword\": \"First-password-1\"}")
            .andExpect(status().isNoContent());
        login(email, "First-password-1").andExpect(status().isOk());
    }

    private MockHttpServletResponse finishGoogleLogin(DefaultOidcUser user) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response,
                new OAuth2AuthenticationToken(user, user.getAuthorities(), GoogleLoginConfig.REGISTRATION_ID));
        assertThat(request.getSession(false)).as("login session ended").isNull();
        return response;
    }

    private String userIdAfter(MockHttpServletResponse response) throws Exception {
        return session(refresh(response.getCookie(REFRESH_COOKIE).getValue()).andReturn()).userId();
    }

    private static DefaultOidcUser googleUser(String subject, String email, boolean verified, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("email_verified", verified);
        claims.put("name", name);
        claims.put("iss", "https://accounts.google.com");
        Instant now = Instant.now();
        return new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"),
                new OidcIdToken("id-token", now, now.plusSeconds(3600), claims));
    }
}
