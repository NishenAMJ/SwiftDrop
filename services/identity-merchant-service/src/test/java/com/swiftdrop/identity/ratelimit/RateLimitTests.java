package com.swiftdrop.identity.ratelimit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.swiftdrop.identity.ApiTestSupport;

/**
 * Rate limits end to end, with tiny limits. Each test uses its own client IP addresses, so the
 * shared limiter in this test context doesn't carry counts from one test into another.
 */
@TestPropertySource(properties = {
    "swiftdrop.identity.rate-limit.enabled=true",
    "swiftdrop.identity.rate-limit.register-per-ip.requests=3",
    "swiftdrop.identity.rate-limit.login-per-ip.requests=5",
    "swiftdrop.identity.rate-limit.login-failures-per-account.requests=3",
    "swiftdrop.identity.rate-limit.api-key-exchange-per-ip.requests=3",
    "swiftdrop.identity.rate-limit.password-failures-per-user.requests=2",
})
class RateLimitTests extends ApiTestSupport {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Test
    void wrongPasswordsLockOneAccountFromEveryAddress() throws Exception {
        String email = uniqueEmail();
        userService.registerCustomer(email, PASSWORD, "Target", null);

        for (int i = 0; i < 3; i++) {
            loginFrom(newIp(), email, "Wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        // Even the right password waits now, so guessing can't continue from new addresses.
        loginFrom(newIp(), email, PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.detail", containsString("Try again in")));

        // Other accounts are unaffected.
        String other = uniqueEmail();
        userService.registerCustomer(other, PASSWORD, "Other", null);
        loginFrom(newIp(), other, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginClearsEarlierFailures() throws Exception {
        String email = uniqueEmail();
        userService.registerCustomer(email, PASSWORD, "User", null);
        // Each attempt from a new address, so only the per-account limit (3 failures) is in play.
        loginFrom(newIp(), email, "Wrong-password-1").andExpect(status().isUnauthorized());
        loginFrom(newIp(), email, "Wrong-password-2").andExpect(status().isUnauthorized());
        loginFrom(newIp(), email, PASSWORD).andExpect(status().isOk());
        loginFrom(newIp(), email, "Wrong-password-3").andExpect(status().isUnauthorized());
        loginFrom(newIp(), email, "Wrong-password-4").andExpect(status().isUnauthorized());
        loginFrom(newIp(), email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void oneAddressCanOnlyTrySoManyLogins() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 5; i++) {
            loginFrom(ip, uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized());
        }
        loginFrom(ip, uniqueEmail(), PASSWORD).andExpect(status().isTooManyRequests());
        loginFrom(newIp(), uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void oneAddressCanOnlyRegisterSoManyAccounts() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 3; i++) {
            registerFrom(ip, uniqueEmail()).andExpect(status().isCreated());
        }
        registerFrom(ip, uniqueEmail()).andExpect(status().isTooManyRequests());
    }

    @Test
    void guessingTheCurrentPasswordWithAStolenTokenIsLimited() throws Exception {
        String token = session(registerFrom(newIp(), uniqueEmail()).andReturn()).accessToken();

        changePassword(token, "Wrong-password-1").andExpect(status().isForbidden());
        changePassword(token, "Wrong-password-2").andExpect(status().isForbidden());
        changePassword(token, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void apiKeyExchangeIsLimitedPerAddress() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 3; i++) {
            exchangeFrom(ip).andExpect(status().isUnauthorized());
        }
        exchangeFrom(ip).andExpect(status().isTooManyRequests());
    }

    private ResultActions loginFrom(String ip, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(from(ip))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions registerFrom(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(from(ip))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\": \"CUSTOMER\", \"email\": \"%s\", \"password\": \"%s\", \"fullName\": \"New User\"}".formatted(email, PASSWORD)));
    }

    private ResultActions exchangeFrom(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/token").with(from(ip)).header("X-API-Key", "sd_live_" + "x".repeat(32)));
    }

    private ResultActions changePassword(String token, String current) throws Exception {
        return postWithToken("/api/v1/users/me/password", token,
                "{\"currentPassword\": \"%s\", \"newPassword\": \"New-password-99\"}".formatted(current));
    }

    private static String newIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
