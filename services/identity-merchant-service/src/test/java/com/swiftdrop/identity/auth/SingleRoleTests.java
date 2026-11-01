package com.swiftdrop.identity.auth;

import com.swiftdrop.identity.ApiTestSupport;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

class SingleRoleTests extends ApiTestSupport {
    @Autowired AuthService auth;
    @Autowired JwtEncoder encoder;

    @Test void preMigrationTokensAreRejectedEvenWithValidSignature() throws Exception {
        var user = registerAndLogin();
        var claims = JwtClaimsSet.builder().issuer("swiftdrop-identity").audience(List.of("swiftdrop-api"))
            .subject(user.userId()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
            .claim("roles", List.of("CUSTOMER", "MERCHANT")).claim("auth", "password").build();
        String legacy = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        getWithToken("/api/v1/users/me", legacy).andExpect(status().isUnauthorized());
    }


    @Test void registrationRequiresOnePublicRole() throws Exception {
        for (String role : new String[]{"CUSTOMER", "MERCHANT", "COURIER"}) {
            var result = register(uniqueEmail(), PASSWORD, role)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.accountRole").value(role))
                .andExpect(jsonPath("$.user.onboardingState").value(role.equals("CUSTOMER") ? "READY" : "APPLICATION_REQUIRED"))
                .andExpect(jsonPath("$.user.roles.length()").value(role.equals("CUSTOMER") ? 1 : 0)).andReturn();
            assertThat((Number) claims(session(result).accessToken()).read("$.identity_version")).isEqualTo(2);
        }
        register(uniqueEmail(), PASSWORD, "DISPATCHER").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"missing@example.com","password":"Password-123","fullName":"Test User"}
                """)).andExpect(status().isBadRequest());
    }

    @Test void accountRoleCannotBeChangedAndApplicationsMustMatch() throws Exception {
        var customer = registerAndLogin();
        var merchant = registerAndLogin("MERCHANT");
        String courierBody = """
            {"nationalIdNumber":"200012345678","vehicleType":"BICYCLE"}
            """;
        postWithToken("/api/v1/couriers", customer.accessToken(), courierBody).andExpect(status().isForbidden());
        postWithToken("/api/v1/couriers", merchant.accessToken(), courierBody).andExpect(status().isForbidden());
        postWithToken("/api/v1/users/me/role", customer.accessToken(), "{\"role\":\"MERCHANT\"}")
            .andExpect(status().isConflict());
        getWithToken("/api/v1/users?role=MERCHANT", activeDispatcherToken())
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(merchant.userId())));
    }

    @Test void googleSelectionIsOneTimeAndReturningLoginPreservesRole() throws Exception {
        String subject = "test-" + UUID.randomUUID();
        String email = uniqueEmail();
        String refresh = auth.signInWithGoogle(subject, email, "Google User");
        var initial = session(refresh(refresh).andExpect(jsonPath("$.user.onboardingState").value("ROLE_REQUIRED")).andReturn());
        getWithToken("/api/v1/users", initial.accessToken()).andExpect(status().isForbidden());
        postWithToken("/api/v1/users/me/role", initial.accessToken(), "{\"role\":\"DISPATCHER\"}")
            .andExpect(status().isBadRequest());
        postWithToken("/api/v1/users/me/role", initial.accessToken(), "{\"role\":\"COURIER\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.accountRole").value("COURIER"))
            .andExpect(jsonPath("$.user.onboardingState").value("APPLICATION_REQUIRED"))
            .andExpect(jsonPath("$.user.roles.length()").value(0));
        postWithToken("/api/v1/users/me/role", initial.accessToken(), "{\"role\":\"CUSTOMER\"}")
            .andExpect(status().isConflict());
        refresh(auth.signInWithGoogle(subject, email, "Google User"))
            .andExpect(jsonPath("$.user.accountRole").value("COURIER"));
    }

    @Test void googleCustomerSelectionReturnsActiveCustomerToken() throws Exception {
        var initial = session(refresh(auth.signInWithGoogle("g-" + UUID.randomUUID(), uniqueEmail(), "Google User")).andReturn());
        postWithToken("/api/v1/users/me/role", initial.accessToken(), "{\"role\":\"CUSTOMER\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
            .andExpect(jsonPath("$.user.onboardingState").value("READY"));
    }
}
