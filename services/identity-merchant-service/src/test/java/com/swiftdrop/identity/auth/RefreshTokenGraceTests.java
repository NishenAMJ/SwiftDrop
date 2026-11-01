package com.swiftdrop.identity.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.swiftdrop.identity.ApiTestSupport;

/** Reuse of an already-rotated refresh token within the grace period (default 10s). */
class RefreshTokenGraceTests extends ApiTestSupport {

    @Test
    void twoTabsRefreshingAtOnceDoNotLogTheUserOut() throws Exception {
        Session session = registerAndLogin();
        Session rotated = session(refresh(session.refreshToken()).andExpect(status().isOk()).andReturn());

        // The second tab sends the old cookie a moment later: rejected, but nothing is revoked.
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        refresh(rotated.refreshToken()).andExpect(status().isOk());
    }
}
