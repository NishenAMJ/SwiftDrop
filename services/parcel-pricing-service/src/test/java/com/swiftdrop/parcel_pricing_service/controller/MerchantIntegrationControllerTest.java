package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.config.SecurityConfig;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidApiKeyException;
import com.swiftdrop.parcel_pricing_service.exception.MerchantOrderExceptionHandler;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import com.swiftdrop.parcel_pricing_service.service.MerchantApiAuthService;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** External merchant API: X-API-Key (verified with Identity) or a MERCHANT bearer token. */
@WebMvcTest(controllers = MerchantIntegrationController.class, properties = {"spring.config.import=", "debug=false"})
@Import({SecurityConfig.class, ApiExceptionHandler.class, MerchantOrderExceptionHandler.class})
class MerchantIntegrationControllerTest {

    private static final String URL = "/api/integrations/merchant/orders";

    @Autowired MockMvc mvc;
    @MockitoBean MerchantOrderService orderService;
    @MockitoBean MerchantApiAuthService apiAuthService;
    @MockitoBean JwtDecoder decoder;

    private final MerchantPrincipal merchant = new MerchantPrincipal(USER_ID, MERCHANT_ID, "token");

    @Test
    void validApiKey_createsAnExternalOrder() throws Exception {
        when(apiAuthService.authenticate("key-1")).thenReturn(merchant);
        when(orderService.createOrder(eq(merchant), any(), eq(OrderSource.EXTERNAL_API))).thenReturn(sampleResponse());

        mvc.perform(post(URL).header("X-API-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SWD-2026-000010"));
    }

    @Test
    void merchantBearerToken_isAcceptedWithoutAnApiKey() throws Exception {
        when(orderService.createOrder(any(), any(), eq(OrderSource.EXTERNAL_API))).thenReturn(sampleResponse());

        mvc.perform(post(URL).with(merchantToken())
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isCreated());
        verifyNoInteractions(apiAuthService);
    }

    @Test
    void customerBearerToken_isRejected() throws Exception {
        mvc.perform(post(URL).with(tokenWithRole("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void missingOrUnknownKey_is401() throws Exception {
        when(apiAuthService.authenticate(any())).thenThrow(new InvalidApiKeyException());

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("X-API-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void identityUnavailable_is503() throws Exception {
        when(apiAuthService.authenticate("key-1")).thenThrow(new ApiKeyVerificationUnavailableException());

        mvc.perform(post(URL).header("X-API-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(orderService);
    }
}
