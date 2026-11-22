package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.config.SecurityConfig;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateOrderException;
import com.swiftdrop.parcel_pricing_service.exception.MerchantOrderExceptionHandler;
import com.swiftdrop.parcel_pricing_service.exception.OrderNotFoundException;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security rules (SecurityConfig) and error mapping for the merchant dashboard endpoints. */
@WebMvcTest(controllers = MerchantOrderController.class, properties = {"spring.config.import=", "debug=false"})
@Import({SecurityConfig.class, ApiExceptionHandler.class, MerchantOrderExceptionHandler.class})
class MerchantOrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MerchantOrderService orderService;
    @MockitoBean JwtDecoder decoder;

    @Test
    void withoutATokenEveryEndpointAnswers401() throws Exception {
        mvc.perform(post("/api/merchant/orders").contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/merchant/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/merchant/orders/" + ORDER_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void aCustomerTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/merchant/orders").with(tokenWithRole("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void aMerchantTokenWithoutMerchantIdClaimIsRejected() throws Exception {
        mvc.perform(post("/api/merchant/orders").with(tokenWithRole("MERCHANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void create_takesTheMerchantFromTheTokenAndReturns201WithLocation() throws Exception {
        when(orderService.createOrder(any(), any(), eq(OrderSource.DASHBOARD))).thenReturn(sampleResponse());

        mvc.perform(post("/api/merchant/orders").with(merchantToken())
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/merchant/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.trackingNumber").value("SWD-2026-000010"))
                .andExpect(jsonPath("$.merchantId").value(MERCHANT_ID.toString()))
                .andExpect(jsonPath("$.deliveryFee").value(575.00))
                .andExpect(jsonPath("$.status").value("BOOKED"));

        ArgumentCaptor<MerchantPrincipal> principal = ArgumentCaptor.forClass(MerchantPrincipal.class);
        verify(orderService).createOrder(principal.capture(), any(), eq(OrderSource.DASHBOARD));
        assertThat(principal.getValue().userId()).isEqualTo(USER_ID);
        assertThat(principal.getValue().merchantId()).isEqualTo(MERCHANT_ID);
    }

    @Test
    void create_invalidFieldsAreReportedPerField() throws Exception {
        String bad = validJson("0").replace("0777654321", "call me");

        mvc.perform(post("/api/merchant/orders").with(merchantToken())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.weightKg").exists())
                .andExpect(jsonPath("$.fieldErrors['deliveryAddress.contactPhone']").exists());
        verifyNoInteractions(orderService);
    }

    @Test
    void create_unknownEnumValueIs400() throws Exception {
        mvc.perform(post("/api/merchant/orders").with(merchantToken())
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50").replace("\"COD\"", "\"CARD\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void create_duplicateReferenceIs409() throws Exception {
        when(orderService.createOrder(any(), any(), any())).thenThrow(new DuplicateOrderException("exists"));

        mvc.perform(post("/api/merchant/orders").with(merchantToken())
                        .contentType(MediaType.APPLICATION_JSON).content(validJson("2.50")))
                .andExpect(status().isConflict());
    }

    @Test
    void list_returnsTheMerchantsOrdersWithTotalHeader() throws Exception {
        when(orderService.getOrders(any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/merchant/orders").with(merchantToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(ORDER_ID.toString()));
    }

    @Test
    void get_orderOfAnotherMerchantIs404() throws Exception {
        when(orderService.getOrder(any(), eq(ORDER_ID))).thenThrow(new OrderNotFoundException(ORDER_ID));

        mvc.perform(get("/api/merchant/orders/" + ORDER_ID).with(merchantToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_nonUuidIdIs400() throws Exception {
        mvc.perform(get("/api/merchant/orders/10").with(merchantToken()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }
}
