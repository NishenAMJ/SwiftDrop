package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.common.DuplicateResourceException;
import com.swiftdrop.parcel_pricing_service.common.ResourceNotFoundException;
import com.swiftdrop.parcel_pricing_service.dto.PricingRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingResponse;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleResponse;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryZone;
import com.swiftdrop.parcel_pricing_service.entity.PricingRule;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.repository.PricingRuleRepository;
import com.swiftdrop.parcel_pricing_service.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private ZoneRepository zoneRepository;

    private PricingService pricingService;

    private PricingRule defaultRule;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(pricingRuleRepository, zoneRepository);

        defaultRule = new PricingRule();
        defaultRule.setId(1L);
        defaultRule.setRuleName("Standard Domestic Pricing");
        defaultRule.setRuleCode("STANDARD_DEFAULT");
        defaultRule.setBaseFee(new BigDecimal("300.00"));
        defaultRule.setRatePerKg(new BigDecimal("50.00"));
        defaultRule.setDefaultZoneCharge(new BigDecimal("150.00"));
        defaultRule.setUrgentSurcharge(new BigDecimal("200.00"));
        defaultRule.setUrgentSurchargeEnabled(false); // Surcharge disabled by default
        defaultRule.setMinWeightKg(new BigDecimal("0.01"));
        defaultRule.setMaxWeightKg(new BigDecimal("50.00"));
        defaultRule.setActive(true);
        defaultRule.setDefault(true);
    }

    @Test
    @DisplayName("Calculate fee matching formula: 300 base + (2kg * 50) + 150 zone = 550 LKR")
    void calculateFee_illustrativeFormula_calculatesCorrectTotal() {
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        PricingRequest request = new PricingRequest(
                new BigDecimal("2.00"),
                null,
                null,
                false,
                null
        );

        PricingResponse response = pricingService.calculateFee(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("550.00"), response.totalDeliveryFee());
        assertEquals(new BigDecimal("300.00"), response.baseFee());
        assertEquals(new BigDecimal("100.00"), response.weightCharge());
        assertEquals(new BigDecimal("150.00"), response.zoneCharge());
        assertEquals(new BigDecimal("2.00"), response.weightKg());
        assertEquals(new BigDecimal("50.00"), response.ratePerKg());
        assertEquals(new BigDecimal("0.00"), response.urgentSurcharge());
        assertFalse(response.urgentSurchargeApplied());
        assertEquals("LKR", response.currency());
        assertEquals("STANDARD_DEFAULT", response.appliedRuleCode());
    }

    @Test
    @DisplayName("Calculate fee looking up zone charge from delivery_zones")
    void calculateFee_withZoneCode_looksUpZoneCharge() {
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        DeliveryZone kandyZone = new DeliveryZone(
                "Kandy District",
                "KANDY",
                "Central Province",
                new BigDecimal("300.00"),
                ZoneStatus.ACTIVE,
                "DISTRICT",
                "20000"
        );
        when(zoneRepository.findByCodeIgnoreCase("KANDY")).thenReturn(Optional.of(kandyZone));

        PricingRequest request = new PricingRequest(
                new BigDecimal("3.00"),
                "KANDY",
                null,
                false,
                null
        );

        PricingResponse response = pricingService.calculateFee(request);

        assertNotNull(response);
        // Base: 300.00 + Weight: (3 * 50) = 150.00 + Zone: 300.00 = 750.00 LKR
        assertEquals(new BigDecimal("750.00"), response.totalDeliveryFee());
        assertEquals(new BigDecimal("300.00"), response.zoneCharge());
        assertEquals("KANDY", response.zoneCode());
    }

    @Test
    @DisplayName("Calculate fee with custom zone charge override")
    void calculateFee_withCustomZoneCharge_usesCustomCharge() {
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        PricingRequest request = new PricingRequest(
                new BigDecimal("1.50"),
                null,
                null,
                false,
                new BigDecimal("250.00")
        );

        PricingResponse response = pricingService.calculateFee(request);

        assertNotNull(response);
        // Base: 300.00 + Weight: (1.5 * 50) = 75.00 + Custom Zone: 250.00 = 625.00 LKR
        assertEquals(new BigDecimal("625.00"), response.totalDeliveryFee());
        assertEquals(new BigDecimal("250.00"), response.zoneCharge());
    }

    @Test
    @DisplayName("Calculate fee when weight is null or zero throws ApiException BAD_REQUEST")
    void calculateFee_invalidWeight_throwsBadRequest() {
        PricingRequest zeroWeight = new PricingRequest(BigDecimal.ZERO, "COLOMBO");
        ApiException ex1 = assertThrows(ApiException.class, () -> pricingService.calculateFee(zeroWeight));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatus());
        assertEquals("INVALID_WEIGHT", ex1.getCode());

        PricingRequest negativeWeight = new PricingRequest(new BigDecimal("-1.00"), "COLOMBO");
        ApiException ex2 = assertThrows(ApiException.class, () -> pricingService.calculateFee(negativeWeight));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatus());
    }

    @Test
    @DisplayName("Calculate fee when weight exceeds max allowed weight throws ApiException BAD_REQUEST")
    void calculateFee_weightExceedsMax_throwsBadRequest() {
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        PricingRequest heavy = new PricingRequest(new BigDecimal("60.00"), "COLOMBO");
        ApiException ex = assertThrows(ApiException.class, () -> pricingService.calculateFee(heavy));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("WEIGHT_EXCEEDS_MAXIMUM", ex.getCode());
    }

    @Test
    @DisplayName("Calculate fee with invalid delivery zone throws ApiException BAD_REQUEST")
    void calculateFee_invalidZone_throwsBadRequest() {
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));
        when(zoneRepository.findByCodeIgnoreCase("UNKNOWN_ZONE")).thenReturn(Optional.empty());

        PricingRequest request = new PricingRequest(new BigDecimal("2.00"), "UNKNOWN_ZONE");
        ApiException ex = assertThrows(ApiException.class, () -> pricingService.calculateFee(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_ZONE", ex.getCode());
    }

    @Test
    @DisplayName("Calculate fee with inactive delivery zone throws ApiException BAD_REQUEST")
    void calculateFee_inactiveZone_throwsBadRequest() {
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        DeliveryZone inactiveZone = new DeliveryZone(
                "Flooded Area",
                "FLOOD_ZONE",
                "Temporarily suspended",
                new BigDecimal("200.00"),
                ZoneStatus.INACTIVE,
                "DISTRICT",
                "99999"
        );
        when(zoneRepository.findByCodeIgnoreCase("FLOOD_ZONE")).thenReturn(Optional.of(inactiveZone));

        PricingRequest request = new PricingRequest(new BigDecimal("2.00"), "FLOOD_ZONE");
        ApiException ex = assertThrows(ApiException.class, () -> pricingService.calculateFee(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INACTIVE_ZONE", ex.getCode());
    }

    @Test
    @DisplayName("Calculate fee when requested pricing rule does not exist throws ResourceNotFoundException")
    void calculateFee_missingRule_throwsNotFound() {
        when(pricingRuleRepository.findByRuleCodeIgnoreCase("NON_EXISTENT")).thenReturn(Optional.empty());

        PricingRequest request = new PricingRequest(new BigDecimal("2.00"), null, "NON_EXISTENT");
        assertThrows(ResourceNotFoundException.class, () -> pricingService.calculateFee(request));
    }

    @Test
    @DisplayName("Urgent delivery surcharge is NOT applied when urgentSurchargeEnabled is false")
    void calculateFee_urgentRequested_surchargeNotAppliedWhenDisabled() {
        defaultRule.setUrgentSurchargeEnabled(false); // explicitly disabled
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        PricingRequest request = new PricingRequest(
                new BigDecimal("2.00"),
                null,
                null,
                true, // Urgent requested
                null
        );

        PricingResponse response = pricingService.calculateFee(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("550.00"), response.totalDeliveryFee());
        assertEquals(new BigDecimal("0.00"), response.urgentSurcharge());
        assertFalse(response.urgentSurchargeApplied());
    }

    @Test
    @DisplayName("Urgent delivery surcharge IS applied when urgentSurchargeEnabled is true")
    void calculateFee_urgentRequested_surchargeAppliedWhenEnabled() {
        defaultRule.setUrgentSurchargeEnabled(true); // enabled
        defaultRule.setUrgentSurcharge(new BigDecimal("200.00"));
        when(pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(defaultRule));

        PricingRequest request = new PricingRequest(
                new BigDecimal("2.00"),
                null,
                null,
                true, // Urgent requested
                null
        );

        PricingResponse response = pricingService.calculateFee(request);

        assertNotNull(response);
        // Base: 300 + Weight: 100 + Zone: 150 + Urgent: 200 = 750 LKR
        assertEquals(new BigDecimal("750.00"), response.totalDeliveryFee());
        assertEquals(new BigDecimal("200.00"), response.urgentSurcharge());
        assertTrue(response.urgentSurchargeApplied());
    }

    @Test
    @DisplayName("Create pricing rule persists entity and returns response")
    void createPricingRule_validRequest_persistsAndReturnsResponse() {
        when(pricingRuleRepository.existsByRuleCodeIgnoreCase("EXPRESS_TIER")).thenReturn(false);
        when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> {
            PricingRule rule = inv.getArgument(0);
            rule.setId(2L);
            return rule;
        });

        PricingRuleRequest request = new PricingRuleRequest(
                "Express Tier",
                "EXPRESS_TIER",
                "Fast express delivery pricing",
                new BigDecimal("500.00"),
                new BigDecimal("75.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                true,
                new BigDecimal("0.10"),
                new BigDecimal("30.00"),
                true,
                false
        );

        PricingRuleResponse response = pricingService.createPricingRule(request);

        assertNotNull(response);
        assertEquals("EXPRESS_TIER", response.ruleCode());
        assertEquals("Express Tier", response.ruleName());
        assertEquals(new BigDecimal("500.00"), response.baseFee());
        assertTrue(response.urgentSurchargeEnabled());
        verify(pricingRuleRepository, times(1)).save(any(PricingRule.class));
    }

    @Test
    @DisplayName("Create pricing rule with duplicate code throws DuplicateResourceException")
    void createPricingRule_duplicateCode_throwsConflict() {
        when(pricingRuleRepository.existsByRuleCodeIgnoreCase("DUPLICATE_CODE")).thenReturn(true);

        PricingRuleRequest request = new PricingRuleRequest(
                "Duplicate Rule",
                "DUPLICATE_CODE",
                "Test",
                new BigDecimal("300.00"),
                new BigDecimal("50.00"),
                new BigDecimal("150.00"),
                null, null, null, null, null, null
        );

        assertThrows(DuplicateResourceException.class, () -> pricingService.createPricingRule(request));
        verify(pricingRuleRepository, never()).save(any(PricingRule.class));
    }

    @Test
    @DisplayName("Update pricing rule updates fields and returns response")
    void updatePricingRule_validRequest_updatesAndReturns() {
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(defaultRule));
        when(pricingRuleRepository.existsByRuleCodeIgnoreCaseAndIdNot("STANDARD_UPDATED", 1L)).thenReturn(false);
        when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PricingRuleRequest updateReq = new PricingRuleRequest(
                "Standard Updated",
                "STANDARD_UPDATED",
                "Updated description",
                new BigDecimal("350.00"),
                new BigDecimal("60.00"),
                new BigDecimal("180.00"),
                new BigDecimal("250.00"),
                false,
                new BigDecimal("0.05"),
                new BigDecimal("45.00"),
                true,
                true
        );

        PricingRuleResponse response = pricingService.updatePricingRule(1L, updateReq);

        assertNotNull(response);
        assertEquals("STANDARD_UPDATED", response.ruleCode());
        assertEquals(new BigDecimal("350.00"), response.baseFee());
        assertEquals(new BigDecimal("60.00"), response.ratePerKg());
        verify(pricingRuleRepository, times(1)).save(any(PricingRule.class));
    }

    @Test
    @DisplayName("Get all pricing rules returns list of responses")
    void getAllPricingRules_returnsAllConfiguredRules() {
        when(pricingRuleRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(defaultRule));

        List<PricingRuleResponse> list = pricingService.getAllPricingRules();

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("STANDARD_DEFAULT", list.get(0).ruleCode());
    }
}
