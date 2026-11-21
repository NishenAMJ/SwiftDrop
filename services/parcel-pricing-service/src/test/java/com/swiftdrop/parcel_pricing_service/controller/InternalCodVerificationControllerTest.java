package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.config.SecurityConfig;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The internal COD lookup: shared key only, money and ids only, and absent unless a key is configured. */
@WebMvcTest(controllers = InternalCodVerificationController.class,
        properties = {"spring.config.import=", "debug=false", "swiftdrop.internal.api-key=test-key-123"})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class InternalCodVerificationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ParcelRepository parcels;
    @MockitoBean JwtDecoder decoder;

    private final UUID id = UUID.randomUUID();
    private final UUID merchant = UUID.randomUUID();

    private Parcel codParcel() {
        var parcel = mock(Parcel.class);
        when(parcel.getId()).thenReturn(id);
        when(parcel.getTrackingNumber()).thenReturn("SWD-2026-000321");
        when(parcel.getMerchantId()).thenReturn(merchant);
        when(parcel.getPaymentMethod()).thenReturn("COD");
        when(parcel.getCodAmount()).thenReturn(new BigDecimal("2500.00"));
        when(parcel.getPricingSnapshotId()).thenReturn("SNP-ABC");
        return parcel;
    }

    @Test
    void theRightKeyGetsTheCodFactsAndNothingElse() throws Exception {
        var parcel = codParcel();
        when(parcels.findById(id)).thenReturn(Optional.of(parcel));

        mvc.perform(get("/api/v1/internal/parcels/" + id + "/cod-verification").header("X-Internal-Key", "test-key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(id.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchant.toString()))
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.codAmount").value(2500.00))
                .andExpect(jsonPath("$.pricingSnapshotId").value("SNP-ABC"))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.pickupAddress").doesNotExist())
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());
    }

    @Test
    void aMissingOrWrongKeyIsRefusedBeforeAnyLookup() throws Exception {
        mvc.perform(get("/api/v1/internal/parcels/" + id + "/cod-verification")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/internal/parcels/" + id + "/cod-verification").header("X-Internal-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(parcels);
    }

    @Test
    void anUnknownOrMalformedBookingIdIsNotFound() throws Exception {
        when(parcels.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/internal/parcels/" + id + "/cod-verification").header("X-Internal-Key", "test-key-123"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/internal/parcels/not-a-uuid/cod-verification").header("X-Internal-Key", "test-key-123"))
                .andExpect(status().isNotFound());
    }
}
