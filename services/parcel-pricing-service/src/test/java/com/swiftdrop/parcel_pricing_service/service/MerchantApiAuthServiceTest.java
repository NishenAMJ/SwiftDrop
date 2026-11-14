package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidApiKeyException;
import com.swiftdrop.parcel_pricing_service.security.MerchantApiKeyVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantApiAuthServiceTest {

    @Mock
    private MerchantApiKeyVerifier verifier;

    @InjectMocks
    private MerchantApiAuthService authService;

    @Test
    void authenticate_missingOrBlankKey_isRejectedWithoutCallingTheVerifier() {
        assertThatThrownBy(() -> authService.authenticate(null)).isInstanceOf(InvalidApiKeyException.class);
        assertThatThrownBy(() -> authService.authenticate("   ")).isInstanceOf(InvalidApiKeyException.class);

        verifyNoInteractions(verifier);
    }

    @Test
    void authenticate_oversizedKey_isRejectedWithoutCallingTheVerifier() {
        assertThatThrownBy(() -> authService.authenticate("k".repeat(257))).isInstanceOf(InvalidApiKeyException.class);

        verifyNoInteractions(verifier);
    }

    @Test
    void authenticate_unknownKey_isRejectedWithGenericMessage() {
        when(verifier.verify("secret-key-abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate("secret-key-abc"))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessage("Invalid or missing API credentials")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("secret-key-abc"));
    }

    @Test
    void authenticate_validKey_returnsOwningMerchantId() {
        when(verifier.verify("key-1")).thenReturn(Optional.of(7L));

        assertThat(authService.authenticate("  key-1 ")).isEqualTo(7L);
    }

    @Test
    void authenticate_verifierUnavailable_propagatesSoTheRequestFailsClosed() {
        when(verifier.verify("key-1")).thenThrow(new ApiKeyVerificationUnavailableException());

        assertThatThrownBy(() -> authService.authenticate("key-1"))
                .isInstanceOf(ApiKeyVerificationUnavailableException.class);
    }
}
