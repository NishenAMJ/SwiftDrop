package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateOrderException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidApiKeyException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidOrderException;
import com.swiftdrop.parcel_pricing_service.exception.MerchantOrderExceptionHandler;
import com.swiftdrop.parcel_pricing_service.service.MerchantApiAuthService;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.sampleResponse;
import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.validJson;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantIntegrationControllerTest {

    private static final String URL = "/api/integrations/merchant/orders";

    @Mock
    private MerchantOrderService orderService;

    @Mock
    private MerchantApiAuthService apiAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantIntegrationController(orderService, apiAuthService))
                .setControllerAdvice(new MerchantOrderExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void create_validKey_returns201AndUsesExternalSource() throws Exception {
        when(apiAuthService.authenticate("key-123")).thenReturn(7L);
        when(orderService.createOrder(eq(7L), any(), eq(OrderSource.EXTERNAL_API))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("MO-00000010"))
                .andExpect(jsonPath("$.merchantId").value(7));
    }

    @Test
    void create_missingKey_returns401AndCreatesNothing() throws Exception {
        when(apiAuthService.authenticate(null)).thenThrow(new InvalidApiKeyException());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or missing API credentials"));

        verify(orderService, never()).createOrder(any(), any(), any());
    }

    @Test
    void create_invalidKey_returns401AndNeverEchoesTheKey() throws Exception {
        when(apiAuthService.authenticate("bad-key-999")).thenThrow(new InvalidApiKeyException());

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "bad-key-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("bad-key-999"))));

        verify(orderService, never()).createOrder(any(), any(), any());
    }

    @Test
    void create_verifierUnavailable_returns503AndCreatesNothing() throws Exception {
        when(apiAuthService.authenticate("key-123")).thenThrow(new ApiKeyVerificationUnavailableException());

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isServiceUnavailable());

        verify(orderService, never()).createOrder(any(), any(), any());
    }

    @Test
    void create_duplicateReference_returns409() throws Exception {
        when(apiAuthService.authenticate("key-123")).thenReturn(7L);
        when(orderService.createOrder(eq(7L), any(), eq(OrderSource.EXTERNAL_API)))
                .thenThrow(new DuplicateOrderException("An order with merchantReference 'SHOP-1001' already exists"));

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An order with merchantReference 'SHOP-1001' already exists"));
    }

    @Test
    void create_missingMerchantReference_returns400() throws Exception {
        when(apiAuthService.authenticate("key-123")).thenReturn(7L);
        when(orderService.createOrder(eq(7L), any(), eq(OrderSource.EXTERNAL_API)))
                .thenThrow(new InvalidOrderException("merchantReference is required for orders created through the external API"));

        mockMvc.perform(post(URL)
                        .header("X-API-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50").replace("\"merchantReference\": \"SHOP-1001\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "merchantReference is required for orders created through the external API"));
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-API-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.weightKg").exists());

        verify(orderService, never()).createOrder(any(), any(), any());
    }
}
