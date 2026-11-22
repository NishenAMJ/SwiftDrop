package com.swiftdrop.parcel_pricing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.common.DuplicateResourceException;
import com.swiftdrop.parcel_pricing_service.common.ResourceNotFoundException;
import com.swiftdrop.parcel_pricing_service.dto.PricingRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingResponse;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleRequest;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryZone;
import com.swiftdrop.parcel_pricing_service.entity.PricingRule;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.entity.ZoneType;
import com.swiftdrop.parcel_pricing_service.repository.PricingRuleRepository;
import com.swiftdrop.parcel_pricing_service.repository.ZoneRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/** Weight limits, rule selection/fallback, zone resolution, rounding and rule-management edge cases. */
class PricingBoundaryTest {
    private final PricingRuleRepository rules = mock(PricingRuleRepository.class);
    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final PricingService service = new PricingService(rules, zones);
    private PricingRule rule;

    @BeforeEach
    void setUp() {
        rule = new PricingRule();
        rule.setId(1L);
        rule.setRuleName("Standard");
        rule.setRuleCode("STANDARD");
        rule.setBaseFee(new BigDecimal("300.00"));
        rule.setRatePerKg(new BigDecimal("50.00"));
        rule.setDefaultZoneCharge(new BigDecimal("150.00"));
        rule.setUrgentSurcharge(new BigDecimal("200.00"));
        rule.setUrgentSurchargeEnabled(true);
        rule.setMinWeightKg(new BigDecimal("0.50"));
        rule.setMaxWeightKg(new BigDecimal("30.00"));
        rule.setActive(true);
        rule.setDefault(true);
        when(rules.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(rule));
    }

    private PricingResponse quote(String weight) {
        return service.calculateFee(new PricingRequest(new BigDecimal(weight), null, null, false, null));
    }

    private static void assertBadRequest(String code, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---------- weight ----------

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-5"})
    void zeroAndNegativeWeightsAreRejected(String weight) {
        assertBadRequest("INVALID_WEIGHT", () -> quote(weight));
    }

    @Test
    void nullWeightIsRejectedBeforeAnyRuleLookup() {
        assertBadRequest("INVALID_WEIGHT", () -> service.calculateFee(new PricingRequest(null, null)));
        verify(rules, never()).findFirstByIsDefaultTrueAndActiveTrue();
    }

    @Test
    void weightBelowTheRuleMinimumIsRejected() {
        assertBadRequest("WEIGHT_BELOW_MINIMUM", () -> quote("0.49"));
    }

    @Test
    void weightExactlyAtTheMinimumAndMaximumIsAccepted() {
        assertThat(quote("0.50").totalDeliveryFee()).isEqualByComparingTo("475.00");   // 300 + 25 + 150
        assertThat(quote("30.00").totalDeliveryFee()).isEqualByComparingTo("1950.00"); // 300 + 1500 + 150
    }

    @Test
    void weightJustAboveTheMaximumIsRejected() {
        assertBadRequest("WEIGHT_EXCEEDS_MAXIMUM", () -> quote("30.01"));
    }

    @Test
    void ruleWithoutWeightLimitsAcceptsAnyPositiveWeight() {
        rule.setMinWeightKg(null);
        rule.setMaxWeightKg(null);
        assertThat(quote("0.01").weightCharge()).isEqualByComparingTo("0.50");
        assertThat(quote("1000").weightCharge()).isEqualByComparingTo("50000.00");
    }

    // ---------- rounding ----------

    @ParameterizedTest
    @CsvSource({"1.005,33.50", "1.004,33.46", "2.333,77.76", "0.015,0.50"})
    void weightChargeIsRoundedHalfUpToCents(String weight, String expectedWeightCharge) {
        rule.setMinWeightKg(null);
        rule.setRatePerKg(new BigDecimal("33.33"));
        PricingResponse response = quote(weight);
        assertThat(response.weightCharge()).isEqualByComparingTo(expectedWeightCharge);
        assertThat(response.weightCharge().scale()).isEqualTo(2);
        assertThat(response.totalDeliveryFee())
                .isEqualByComparingTo(response.baseFee().add(response.weightCharge()).add(response.zoneCharge()));
    }

    @Test
    void customZoneChargeIsRoundedAndLabelledWhenNoZoneIsGiven() {
        PricingResponse response = service.calculateFee(
                new PricingRequest(new BigDecimal("1"), null, null, false, new BigDecimal("99.995")));
        assertThat(response.zoneCharge()).isEqualByComparingTo("100.00");
        assertThat(response.zoneCode()).isEqualTo("CUSTOM");
        verify(zones, never()).findByCodeIgnoreCase(any());
    }

    @Test
    void customZoneChargeKeepsTheNormalisedZoneCode() {
        PricingResponse response = service.calculateFee(
                new PricingRequest(new BigDecimal("1"), " cmb-01 ", null, false, new BigDecimal("10")));
        assertThat(response.zoneCode()).isEqualTo("CMB-01");
    }

    // ---------- zones ----------

    @Test
    void zoneWithoutItsOwnChargeFallsBackToTheRuleDefault() {
        DeliveryZone zone = new DeliveryZone("Cmb", "CMB", null, ZoneType.GENERAL);
        zone.setStatus(ZoneStatus.ACTIVE);
        zone.setBaseCharge(null);
        when(zones.findByCodeIgnoreCase("CMB")).thenReturn(Optional.of(zone));
        PricingResponse response = service.calculateFee(new PricingRequest(new BigDecimal("1"), " cmb "));
        assertThat(response.zoneCharge()).isEqualByComparingTo("150.00");
        assertThat(response.zoneCode()).isEqualTo("CMB");
    }

    @Test
    void blankZoneCodeUsesTheStandardZoneCharge() {
        PricingResponse response = service.calculateFee(new PricingRequest(new BigDecimal("1"), "   "));
        assertThat(response.zoneCode()).isEqualTo("STANDARD_ZONE");
        verify(zones, never()).findByCodeIgnoreCase(any());
    }

    // ---------- rule selection ----------

    @Test
    void namedRuleIsUsedEvenWhenADefaultExists() {
        PricingRule express = new PricingRule();
        express.setRuleCode("EXPRESS");
        express.setBaseFee(new BigDecimal("500"));
        express.setRatePerKg(new BigDecimal("100"));
        express.setDefaultZoneCharge(new BigDecimal("0"));
        express.setActive(true);
        when(rules.findByRuleCodeIgnoreCase("express")).thenReturn(Optional.of(express));

        PricingResponse response = service.calculateFee(new PricingRequest(new BigDecimal("2"), null, " express "));
        assertThat(response.appliedRuleCode()).isEqualTo("EXPRESS");
        assertThat(response.totalDeliveryFee()).isEqualByComparingTo("700.00");
    }

    @Test
    void inactiveNamedRuleIsRejected() {
        PricingRule retired = new PricingRule();
        retired.setRuleCode("RETIRED");
        retired.setActive(false);
        when(rules.findByRuleCodeIgnoreCase("RETIRED")).thenReturn(Optional.of(retired));
        assertBadRequest("INACTIVE_PRICING_RULE",
                () -> service.calculateFee(new PricingRequest(new BigDecimal("1"), null, "RETIRED")));
    }

    @Test
    void withoutADefaultTheOldestActiveRuleIsUsed() {
        when(rules.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.empty());
        rule.setRuleCode("OLDEST_ACTIVE");
        when(rules.findFirstByActiveTrueOrderByCreatedAtAsc()).thenReturn(Optional.of(rule));
        assertThat(quote("1").appliedRuleCode()).isEqualTo("OLDEST_ACTIVE");
    }

    @Test
    void noActiveRuleAtAllIsNotFound() {
        when(rules.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.empty());
        when(rules.findFirstByActiveTrueOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> quote("1")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- urgent ----------

    @Test
    void urgentSurchargeIsOnlyAddedWhenRequestedAndEnabled() {
        PricingResponse urgent = service.calculateFee(new PricingRequest(new BigDecimal("1"), null, null, true, null));
        assertThat(urgent.urgentSurchargeApplied()).isTrue();
        assertThat(urgent.totalDeliveryFee()).isEqualByComparingTo("700.00");
        assertThat(urgent.breakdownDescription()).contains("Urgent Surcharge");

        PricingResponse unspecified = service.calculateFee(new PricingRequest(new BigDecimal("1"), null, null, null, null));
        assertThat(unspecified.urgentSurchargeApplied()).isFalse();
        assertThat(unspecified.urgentSurcharge()).isEqualByComparingTo("0");
    }

    // ---------- rule management ----------

    private static PricingRuleRequest ruleRequest(String code, Boolean isDefault, Boolean active,
                                                  BigDecimal urgent, Boolean urgentEnabled, BigDecimal min) {
        return new PricingRuleRequest(" Express ", code, null, new BigDecimal("500"), new BigDecimal("100"),
                new BigDecimal("0"), urgent, urgentEnabled, min, new BigDecimal("20"), active, isDefault);
    }

    @Test
    void getRuleByIdReturnsTheRuleOrNotFound() {
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        assertThat(service.getPricingRuleById(1L).ruleCode()).isEqualTo("STANDARD");
        when(rules.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPricingRuleById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creatingADefaultRuleClearsOtherDefaultsAndAppliesSafeDefaults() {
        when(rules.existsByRuleCodeIgnoreCase("EXPRESS")).thenReturn(false);
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createPricingRule(ruleRequest(" express ", true, null, null, null, null));

        verify(rules).clearDefaultRules(null);
        assertThat(created.ruleCode()).isEqualTo("EXPRESS");
        assertThat(created.ruleName()).isEqualTo("Express");
        assertThat(created.urgentSurcharge()).isEqualByComparingTo("200.00");
        assertThat(created.urgentSurchargeEnabled()).isFalse();
        assertThat(created.minWeightKg()).isEqualByComparingTo("0.01");
        assertThat(created.active()).isTrue();
    }

    @Test
    void updatingToAnotherRulesCodeIsADuplicate() {
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(rules.existsByRuleCodeIgnoreCaseAndIdNot("EXPRESS", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.updatePricingRule(1L, ruleRequest("express", false, null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(rules, never()).save(any());
    }

    @Test
    void updatingAMissingRuleIsNotFound() {
        when(rules.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updatePricingRule(7L, ruleRequest("X", false, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void partialUpdateKeepsOptionalFieldsThatWereNotSent() {
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(rules.existsByRuleCodeIgnoreCaseAndIdNot("STANDARD", 1L)).thenReturn(false);
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updatePricingRule(1L, ruleRequest("standard", true, null, null, null, null));

        verify(rules).clearDefaultRules(1L);
        assertThat(updated.urgentSurcharge()).isEqualByComparingTo("200.00");
        assertThat(updated.urgentSurchargeEnabled()).isTrue();
        assertThat(updated.minWeightKg()).isEqualByComparingTo("0.50");
        assertThat(updated.active()).isTrue();
    }

    @Test
    void updateCanDeactivateARuleAndChangeOptionalFields() {
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updatePricingRule(1L,
                ruleRequest("STANDARD", false, false, new BigDecimal("350"), false, new BigDecimal("1")));

        assertThat(updated.active()).isFalse();
        assertThat(updated.urgentSurcharge()).isEqualByComparingTo("350");
        assertThat(updated.urgentSurchargeEnabled()).isFalse();
        assertThat(updated.minWeightKg()).isEqualByComparingTo("1");
        verify(rules, never()).clearDefaultRules(any());
    }
}
