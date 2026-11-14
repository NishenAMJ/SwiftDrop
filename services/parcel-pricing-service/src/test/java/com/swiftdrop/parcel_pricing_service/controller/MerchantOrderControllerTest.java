package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.exception.MerchantOrderExceptionHandler;
import com.swiftdrop.parcel_pricing_service.exception.OrderAccessDeniedException;
import com.swiftdrop.parcel_pricing_service.exception.OrderNotFoundException;
import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import com.swiftdrop.parcel_pricing_service.security.MerchantIdentityResolver;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.sampleResponse;
import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.validJson;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc tests (no Spring context, no Boot-version-specific test annotations). */
@ExtendWith(MockitoExtension.class)
class MerchantOrderControllerTest {

    @Mock
    private MerchantOrderService orderService;

    @Mock
    private MerchantIdentityResolver identityResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantOrderController(orderService, identityResolver))
                .setControllerAdvice(new MerchantOrderExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ---------- POST /api/merchant/orders ----------

    @Test
    void create_valid_returns201WithLocation() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenReturn(7L);
        when(orderService.createOrder(eq(7L), any(), eq(OrderSource.DASHBOARD))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/merchant/orders/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.merchantId").value(7))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.deliveryFee").value(450.00));
    }

    @Test
    void create_unauthenticated_returns401AndCreatesNothing() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenThrow(new UnauthenticatedMerchantException());

        mockMvc.perform(post("/api/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("2.50")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(orderService, never()).createOrder(any(), any(), any());
    }

    @Test
    void create_invalidWeight_returns400WithFieldError() throws Exception {
        for (String weight : List.of("0", "-5", "50.01", "1.234")) {
            mockMvc.perform(post("/api/merchant/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validJson(weight)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.weightKg").exists());
        }
        verify(orderService, never()).createOrder(any(), any(), any());
    }

    @Test
    void create_missingRequiredFields_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sender").exists())
                .andExpect(jsonPath("$.fieldErrors.receiver").exists())
                .andExpect(jsonPath("$.fieldErrors.pickupAddress").exists())
                .andExpect(jsonPath("$.fieldErrors.deliveryAddress").exists())
                .andExpect(jsonPath("$.fieldErrors.parcelDescription").exists())
                .andExpect(jsonPath("$.fieldErrors.weightKg").exists())
                .andExpect(jsonPath("$.fieldErrors.deliveryType").exists())
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").exists());
    }

    @Test
    void create_invalidNestedContact_reportsNestedFieldPath() throws Exception {
        String body = validJson("2.50").replace("+94771234567", "not-a-phone");

        mockMvc.perform(post("/api/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['sender.phone']").exists());
    }

    @Test
    void create_invalidEnumValue_returns400() throws Exception {
        String body = validJson("2.50").replace("\"NORMAL\"", "\"SUPERFAST\"");

        mockMvc.perform(post("/api/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/merchant/orders ----------

    @Test
    void list_returnsOwnOrdersWithTotalHeader() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenReturn(7L);
        when(orderService.getOrders(7L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/merchant/orders"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].orderNumber").value("MO-00000010"));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenThrow(new UnauthenticatedMerchantException());

        mockMvc.perform(get("/api/merchant/orders")).andExpect(status().isUnauthorized());
        verify(orderService, never()).getOrders(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void list_nonNumericPage_returns400() throws Exception {
        mockMvc.perform(get("/api/merchant/orders").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'page' has an invalid value"));
    }

    // ---------- GET /api/merchant/orders/{id} ----------

    @Test
    void getById_ownOrder_returns200() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenReturn(7L);
        when(orderService.getOrder(7L, 10L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/merchant/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getById_unknown_returns404() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenReturn(7L);
        when(orderService.getOrder(7L, 99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/merchant/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Merchant order with id 99 was not found"));
    }

    @Test
    void getById_otherMerchantsOrder_returns403WithoutOrderData() throws Exception {
        when(identityResolver.resolveMerchantId(any())).thenReturn(7L);
        when(orderService.getOrder(7L, 11L)).thenThrow(new OrderAccessDeniedException());

        mockMvc.perform(get("/api/merchant/orders/11"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this order"))
                .andExpect(jsonPath("$.orderNumber").doesNotExist())
                .andExpect(jsonPath("$.sender").doesNotExist());
    }

    @Test
    void getById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/merchant/orders/abc"))
                .andExpect(status().isBadRequest());
    }
}
