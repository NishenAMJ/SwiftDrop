package com.swiftdrop.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.user.User;
import com.swiftdrop.identity.user.UserService;

/**
 * Helpers for end-to-end tests against the real database. Every test uses fresh random emails,
 * so tests don't depend on each other or on what earlier runs left in the database. Rate limiting
 * is off here (see RateLimitTests).
 */
@SpringBootTest
@AutoConfigureMockMvc
// Tests make many requests from one address; RateLimitTests turns limiting back on.
@TestPropertySource(properties = "swiftdrop.identity.rate-limit.enabled=false")
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "Correct-horse-42";
    protected static final String REFRESH_COOKIE = "sd_refresh";
    protected static final String INITIAL_PASSWORD = "Initial-pass-1";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserService userService;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "password": "%s", "fullName": "Test User", "phoneNumber": "+94771234567"}
                """.formatted(email, password)));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshToken)));
    }

    protected ResultActions getWithToken(String path, String accessToken) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** POST with an optional JSON body ({@code null} sends no body). */
    protected ResultActions postWithToken(String path, String accessToken, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions deleteWithToken(String path, String accessToken) throws Exception {
        return mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** A dispatcher who has already replaced the initial password. */
    protected String activeDispatcherToken() throws Exception {
        String email = uniqueEmail();
        User user = userService.createDispatcher(email, "Dispatcher", null, INITIAL_PASSWORD);
        userService.changePassword(user.getId(), INITIAL_PASSWORD, PASSWORD);
        return session(login(email, PASSWORD).andReturn()).accessToken();
    }

    /** A logged-in session: access token, refresh token, user ID. */
    protected record Session(String accessToken, String refreshToken, String userId) {
    }

    protected static Session session(MvcResult result) throws Exception {
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new Session(body.read("$.accessToken"), cookie == null ? null : cookie.getValue(),
                body.read("$.user.id"));
    }

    protected Session registerAndLogin() throws Exception {
        return session(register(uniqueEmail(), PASSWORD).andReturn());
    }

    /** The decoded payload of a JWT (no signature check; the API tests cover that). */
    protected static DocumentContext claims(String jwt) {
        return JsonPath.parse(decodePart(jwt, 1));
    }

    protected static DocumentContext jwtHeader(String jwt) {
        return JsonPath.parse(decodePart(jwt, 0));
    }

    private static String decodePart(String jwt, int index) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8);
    }
}
