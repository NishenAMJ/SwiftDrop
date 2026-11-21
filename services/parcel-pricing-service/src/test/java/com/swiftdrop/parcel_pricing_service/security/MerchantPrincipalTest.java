package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantPrincipalTest {

    private final UUID user = UUID.randomUUID();
    private final UUID merchant = UUID.randomUUID();

    private Jwt.Builder token() {
        return Jwt.withTokenValue("secret-token-value").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void merchantToken_givesUserAndMerchantIds() {
        Jwt jwt = token().subject(user.toString()).claim("roles", List.of("MERCHANT"))
                .claim("merchant_id", merchant.toString()).build();

        MerchantPrincipal principal = MerchantPrincipal.fromJwt(jwt);

        assertThat(principal.userId()).isEqualTo(user);
        assertThat(principal.merchantId()).isEqualTo(merchant);
        assertThat(principal.accessToken()).isEqualTo("secret-token-value");
        assertThat(principal.toString()).doesNotContain("secret-token-value");
    }

    @Test
    void notAMerchant_orMissingIds_isRejected() {
        Jwt customer = token().subject(user.toString()).claim("roles", List.of("CUSTOMER"))
                .claim("merchant_id", merchant.toString()).build();
        Jwt noMerchantId = token().subject(user.toString()).claim("roles", List.of("MERCHANT")).build();
        Jwt badSubject = token().subject("not-a-uuid").claim("roles", List.of("MERCHANT"))
                .claim("merchant_id", merchant.toString()).build();

        assertThatThrownBy(() -> MerchantPrincipal.fromJwt(null)).isInstanceOf(UnauthenticatedMerchantException.class);
        assertThatThrownBy(() -> MerchantPrincipal.fromJwt(customer)).isInstanceOf(UnauthenticatedMerchantException.class);
        assertThatThrownBy(() -> MerchantPrincipal.fromJwt(noMerchantId)).isInstanceOf(UnauthenticatedMerchantException.class);
        assertThatThrownBy(() -> MerchantPrincipal.fromJwt(badSubject)).isInstanceOf(UnauthenticatedMerchantException.class);
    }
}
