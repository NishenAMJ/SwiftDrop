package com.swiftdrop.payment.config;

import com.swiftdrop.payment.ledger.controller.MerchantLedgerController;
import com.swiftdrop.payment.ledger.service.MerchantLedgerService;
import com.swiftdrop.payment.onlinepayment.controller.ProblemHandler;
import com.swiftdrop.payment.reconciliation.controller.CashHandoverController;
import com.swiftdrop.payment.reconciliation.service.CashHandoverService;
import com.swiftdrop.payment.settlement.controller.MerchantSettlementController;
import com.swiftdrop.payment.settlement.service.MerchantSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CashHandoverController.class, MerchantLedgerController.class,
    MerchantSettlementController.class}, properties = {"spring.config.import=", "debug=false"})
@Import({PaymentSecurity.class, ProblemHandler.class})
class Member9SecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CashHandoverService handovers;
    @MockitoBean MerchantLedgerService ledger;
    @MockitoBean MerchantSettlementService settlements;
    @MockitoBean JwtDecoder decoder;

    @Test
    void member9RoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/cod-handovers"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/merchant-ledger/entries"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/merchant-settlements"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/merchant-settlements/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void merchantAndDispatcherCanReachSettlementHistory() throws Exception {
        for (String role : java.util.List.of("MERCHANT", "DISPATCHER")) {
            mvc.perform(get("/api/v1/merchant-settlements")
                .with(jwt().jwt(token -> token
                    .subject("00000000-0000-0000-0000-000000000001")
                    .claim("roles", java.util.List.of(role))
                    .claim("merchant_id", "00000000-0000-0000-0000-000000000002"))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
        }
    }

    @Test
    void onlyCourierCanSubmitHandover() throws Exception {
        mvc.perform(post("/api/v1/cod-handovers")
            .with(role("MERCHANT"))
            .header("Idempotency-Key", "handover-key-0001")
            .contentType("application/json")
            .content("{\"collectionIds\":[\"00000000-0000-0000-0000-000000000001\"],\"submittedAmount\":1000,\"currency\":\"LKR\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void courierCannotConfirmHandoverOrReadMerchantLedger() throws Exception {
        mvc.perform(post("/api/v1/cod-handovers/00000000-0000-0000-0000-000000000001/confirm")
            .with(role("COURIER"))
            .header("Idempotency-Key", "confirmation-key-1")
            .contentType("application/json").content("{\"receivedAmount\":1000}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchant-ledger/entries").with(role("COURIER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchant-settlements").with(role("COURIER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void merchantCannotPostAdjustmentOrCalculateSettlement() throws Exception {
        mvc.perform(post("/api/v1/merchant-ledger/adjustments")
            .with(role("MERCHANT"))
            .header("Idempotency-Key", "adjustment-key-01")
            .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/merchant-settlements/calculate")
            .with(role("MERCHANT"))
            .header("Idempotency-Key", "settlement-key-01")
            .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
