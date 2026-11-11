package com.swiftdrop.payment.onlinepayment.controller;

import com.swiftdrop.payment.config.PaymentSecurity;
import com.swiftdrop.payment.onlinepayment.callback.PaymentCallbackController;
import com.swiftdrop.payment.onlinepayment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={PaymentController.class,PaymentCallbackController.class},properties={"spring.config.import=","debug=false"})
@Import({PaymentSecurity.class,ProblemHandler.class})
class PaymentSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentService service;
    @MockitoBean JwtDecoder decoder;
    @Test void paymentRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/payments/history")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
    @Test void customerCannotRefund() throws Exception {
        mvc.perform(post("/api/v1/payments/00000000-0000-0000-0000-000000000001/refund")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
            .header("Idempotency-Key","unique-key-123456").contentType("application/json").content("{\"amount\":10,\"reason\":\"Test\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void callbackDoesNotRequireCustomerJwtButRejectsDuplicateFields() throws Exception {
        mvc.perform(post("/api/v1/payments/callbacks/payhere").contentType("application/x-www-form-urlencoded")
            .param("merchant_id","1","2")).andExpect(status().isBadRequest());
    }
    @Test void malformedRefundFailsValidation() throws Exception {
        mvc.perform(post("/api/v1/payments/00000000-0000-0000-0000-000000000001/refund")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")))
            .header("Idempotency-Key","unique-key-123456").contentType("application/json").content("{\"amount\":-10,\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
