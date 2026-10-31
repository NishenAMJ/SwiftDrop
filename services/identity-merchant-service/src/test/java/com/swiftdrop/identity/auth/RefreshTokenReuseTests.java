package com.swiftdrop.identity.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.swiftdrop.identity.ApiTestSupport;

/** Reuse of an already-rotated refresh token, outside the grace period. */
@TestPropertySource(properties = "swiftdrop.identity.refresh-token.reuse-grace=0s")
class RefreshTokenReuseTests extends ApiTestSupport {

    @Test
    void reusingAnOldTokenRevokesTheWholeLogin() throws Exception {
        Session session = registerAndLogin();
        Session rotated = session(refresh(session.refreshToken()).andExpect(status().isOk()).andReturn());

        // Someone presents the old token again: treat it as stolen.
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        // The newer token from the same login is revoked too, so a thief can't keep using it.
        refresh(rotated.refreshToken()).andExpect(status().isUnauthorized());
    }
}
