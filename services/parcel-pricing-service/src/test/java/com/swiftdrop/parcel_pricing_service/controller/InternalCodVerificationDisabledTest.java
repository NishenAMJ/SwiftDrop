package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.config.SecurityConfig;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With no key configured (the default), the internal endpoint answers as if it did not exist. */
@WebMvcTest(controllers = InternalCodVerificationController.class,
        properties = {"spring.config.import=", "debug=false", "swiftdrop.internal.api-key="})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class InternalCodVerificationDisabledTest {

    @Autowired MockMvc mvc;
    @MockitoBean ParcelRepository parcels;
    @MockitoBean JwtDecoder decoder;

    @Test
    void noConfiguredKeyMeansNotFoundEvenWithAKeyHeader() throws Exception {
        mvc.perform(get("/api/v1/internal/parcels/" + UUID.randomUUID() + "/cod-verification")
                        .header("X-Internal-Key", "anything"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(parcels);
    }
}
