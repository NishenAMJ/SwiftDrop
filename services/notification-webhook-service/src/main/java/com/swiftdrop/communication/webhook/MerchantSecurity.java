package com.swiftdrop.communication.webhook;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.swiftdrop.communication.common.ApiException;
import com.swiftdrop.communication.common.Problems;

@Component
public class MerchantSecurity {

    public static final String MERCHANT_ID_CLAIM = "merchant_id";

    public UUID requireMerchantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Object claim = jwt.getClaims().get(MERCHANT_ID_CLAIM);
            if (claim != null && !claim.toString().isBlank()) {
                try {
                    return UUID.fromString(claim.toString().trim());
                } catch (IllegalArgumentException e) {
                    throw new ApiException(HttpStatus.FORBIDDEN,
                            Problems.forbidden("MERCHANT_NOT_APPROVED", "Invalid merchant_id claim format."));
                }
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN,
                Problems.forbidden("MERCHANT_NOT_APPROVED", "Merchant account is pending approval or merchant_id claim is missing."));
    }
}
