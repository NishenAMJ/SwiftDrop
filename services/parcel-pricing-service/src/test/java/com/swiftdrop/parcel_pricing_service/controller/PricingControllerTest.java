package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiExceptionHandler;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.common.DuplicateResourceException;
import com.swiftdrop.parcel_pricing_service.common.ResourceNotFoundException;
import com.swiftdrop.parcel_pricing_service.dto.PricingRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingResponse;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleResponse;
import com.swiftdrop.parcel_pricing_service.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PricingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private PricingController pricingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pricingController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/pricing/calculate returns 200 OK with delivery fee breakdown")
    void calculateFee_validRequest_returns200OkWithBreakdown() throws Exception {
        PricingResponse response = new PricingResponse(
                new BigDecimal("550.00"),
                new BigDecimal("300.00"),
                new BigDecimal("100.00"),
                new BigDecimal("2.00"),
                new BigDecimal("50.00"),
                new BigDecimal("150.00"),
                "COLOMBO",
                new BigDecimal("0.00"),
                false,
                "LKR",
                "STANDARD_DEFAULT",
                "Base Fee (LKR 300.00) + Weight Charge (2.00 kg @ LKR 50.00/kg = LKR 100.00) + Zone Charge [COLOMBO] (LKR 150.00) = LKR 550.00"
        );

        when(pricingService.calculateFee(any(PricingRequest.class))).thenReturn(response);

        String json = """
                {
                    "weightKg": 2.00,
                    "zoneCode": "COLOMBO",
                    "ruleCode": "STANDARD_DEFAULT",
                    "isUrgent": false
                }
                """;

        mockMvc.perform(post("/api/pricing/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeliveryFee", is(550.00)))
                .andExpect(jsonPath("$.baseFee", is(300.00)))
                .andExpect(jsonPath("$.weightCharge", is(100.00)))
                .andExpect(jsonPath("$.zoneCharge", is(150.00)))
                .andExpect(jsonPath("$.currency", is("LKR")))
                .andExpect(jsonPath("$.appliedRuleCode", is("STANDARD_DEFAULT")));
    }

    @Test
    @DisplayName("POST /api/pricing/calculate with invalid weight returns 400 Bad Request")
    void calculateFee_invalidWeight_returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                    "weightKg": -1.5,
                    "zoneCode": "COLOMBO"
                }
                """;

        mockMvc.perform(post("/api/pricing/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors.weightKg", notNullValue()));
    }

    @Test
    @DisplayName("Pricing business exception preserves its status and code")
    void businessException_preservesStatusAndCode() throws Exception {
        when(pricingService.calculateFee(any(PricingRequest.class)))
                .thenThrow(ApiException.badRequest("INVALID_WEIGHT", "Parcel weight must be greater than 0 kg"));

        mockMvc.perform(post("/api/pricing/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "weightKg": 2.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_WEIGHT")));
    }

    @Test
    @DisplayName("Resource exceptions map to 404 and 409")
    void resourceExceptions_mapToExpectedStatuses() throws Exception {
        when(pricingService.getPricingRuleById(99L))
                .thenThrow(new ResourceNotFoundException("Pricing rule not found"));
        when(pricingService.createPricingRule(any(PricingRuleRequest.class)))
                .thenThrow(new DuplicateResourceException("DUPLICATE_RULE_CODE", "Rule already exists"));

        mockMvc.perform(get("/api/pricing/rules/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));

        mockMvc.perform(post("/api/pricing/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "ruleName": "Duplicate",
                                    "ruleCode": "DUPLICATE",
                                    "baseFee": 300.00,
                                    "ratePerKg": 50.00,
                                    "defaultZoneCharge": 150.00
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_RULE_CODE")));
    }

    @Test
    @DisplayName("GET /api/pricing/rules returns 200 OK with list of rules")
    void getAllRules_returns200OkWithList() throws Exception {
        PricingRuleResponse rule = new PricingRuleResponse(
                1L,
                "Standard Domestic Pricing",
                "STANDARD_DEFAULT",
                "Default standard pricing",
                new BigDecimal("300.00"),
                new BigDecimal("50.00"),
                new BigDecimal("150.00"),
                new BigDecimal("200.00"),
                false,
                new BigDecimal("0.01"),
                new BigDecimal("50.00"),
                true,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(pricingService.getAllPricingRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/pricing/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ruleCode", is("STANDARD_DEFAULT")))
                .andExpect(jsonPath("$[0].baseFee", is(300.00)));
    }

    @Test
    @DisplayName("POST /api/pricing/rules creates rule and returns 201 Created")
    void createRule_validRequest_returns201Created() throws Exception {
        PricingRuleResponse created = new PricingRuleResponse(
                2L,
                "Express Tier",
                "EXPRESS_TIER",
                "Express delivery pricing",
                new BigDecimal("400.00"),
                new BigDecimal("70.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                false,
                new BigDecimal("0.01"),
                new BigDecimal("40.00"),
                true,
                false,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(pricingService.createPricingRule(any(PricingRuleRequest.class))).thenReturn(created);

        String json = """
                {
                    "ruleName": "Express Tier",
                    "ruleCode": "EXPRESS_TIER",
                    "description": "Express delivery pricing",
                    "baseFee": 400.00,
                    "ratePerKg": 70.00,
                    "defaultZoneCharge": 200.00,
                    "urgentSurcharge": 300.00,
                    "urgentSurchargeEnabled": false,
                    "minWeightKg": 0.01,
                    "maxWeightKg": 40.00,
                    "active": true,
                    "isDefault": false
                }
                """;

        mockMvc.perform(post("/api/pricing/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.ruleCode", is("EXPRESS_TIER")))
                .andExpect(jsonPath("$.baseFee", is(400.00)));
    }

    @Test
    @DisplayName("PUT /api/pricing/rules/{id} updates rule and returns 200 OK")
    void updateRule_validRequest_returns200Ok() throws Exception {
        PricingRuleResponse updated = new PricingRuleResponse(
                1L,
                "Standard Updated",
                "STANDARD_DEFAULT",
                "Updated description",
                new BigDecimal("320.00"),
                new BigDecimal("55.00"),
                new BigDecimal("160.00"),
                new BigDecimal("200.00"),
                false,
                new BigDecimal("0.01"),
                new BigDecimal("50.00"),
                true,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(pricingService.updatePricingRule(eq(1L), any(PricingRuleRequest.class))).thenReturn(updated);

        String json = """
                {
                    "ruleName": "Standard Updated",
                    "ruleCode": "STANDARD_DEFAULT",
                    "description": "Updated description",
                    "baseFee": 320.00,
                    "ratePerKg": 55.00,
                    "defaultZoneCharge": 160.00,
                    "urgentSurcharge": 200.00,
                    "urgentSurchargeEnabled": false,
                    "minWeightKg": 0.01,
                    "maxWeightKg": 50.00,
                    "active": true,
                    "isDefault": true
                }
                """;

        mockMvc.perform(put("/api/pricing/rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.baseFee", is(320.00)))
                .andExpect(jsonPath("$.ratePerKg", is(55.00)));
    }
}
