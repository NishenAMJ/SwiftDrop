package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.config.SecurityConfig;
import com.swiftdrop.parcel_pricing_service.dto.AddressDto;
import com.swiftdrop.parcel_pricing_service.dto.DispatchParcelDto;
import com.swiftdrop.parcel_pricing_service.service.DispatchParcelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only dispatchers may look a parcel up for assignment, and only the dispatch fields come back. */
@WebMvcTest(controllers = DispatchParcelController.class, properties = {"spring.config.import=", "debug=false"})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class DispatchParcelControllerTest {

    private static final String URL = "/api/v1/dispatch/parcels/track/SWD-2026-000001";

    @Autowired MockMvc mvc;
    @MockitoBean DispatchParcelService service;
    @MockitoBean JwtDecoder decoder;

    private RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void aDispatcherGetsTheDispatchFieldsAndNoFees() throws Exception {
        var merchant = UUID.randomUUID();
        when(service.findByTrackingNumber("SWD-2026-000001")).thenReturn(new DispatchParcelDto(
                UUID.randomUUID(), "SWD-2026-000001", UUID.randomUUID(), merchant, "COD",
                new BigDecimal("2500.00"), "SNP-ABC", "LKR",
                new AddressDto("Nimal", "0771234567", "12 Main St", "Colombo", null, "COLOMBO"),
                new AddressDto("Kamala", "0777654321", "45 Lake Rd", "Kandy", null, "KANDY"),
                "BOOKED", new BigDecimal("12.50")));

        mvc.perform(get(URL).with(as("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchant.toString()))
                .andExpect(jsonPath("$.codAmount").value(2500.00))
                .andExpect(jsonPath("$.pricingSnapshotId").value("SNP-ABC"))
                .andExpect(jsonPath("$.deliveryAddress.contactName").value("Kamala"))
                .andExpect(jsonPath("$.deliveryAddress.contactPhone").value("0777654321"))
                .andExpect(jsonPath("$.pickupAddress.streetAddress").value("12 Main St"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.weightKg").value(12.50))
                .andExpect(jsonPath("$.estimatedFee").doesNotExist());
    }

    @Test
    void customersCouriersAndAnonymousCallersAreRefused() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(as("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get(URL).with(as("COURIER"))).andExpect(status().isForbidden());
        mvc.perform(get(URL).with(as("MERCHANT"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anUnknownOrUnpaidParcelIsNotFound() throws Exception {
        when(service.findByTrackingNumber("SWD-2026-000001"))
                .thenThrow(ApiException.notFound("No booking has this tracking number."));

        mvc.perform(get(URL).with(as("DISPATCHER"))).andExpect(status().isNotFound());
    }
}
