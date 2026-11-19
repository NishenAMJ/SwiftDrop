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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final String DEFAULT_CURRENCY = "LKR";

    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneRepository zoneRepository;

    public PricingService(PricingRuleRepository pricingRuleRepository,
                          ZoneRepository zoneRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Calculate delivery fee using: Delivery Fee = Base Fee + Weight Charge + Zone Charge (+ Urgent Surcharge if enabled)
     */
    @Transactional(readOnly = true)
    public PricingResponse calculateFee(PricingRequest request) {
        log.info("Calculating delivery fee for request: weight={}, zone={}, rule={}, urgent={}",
                request.weightKg(), request.zoneCode(), request.ruleCode(), request.isUrgent());

        // 1. Validate Weight
        BigDecimal weight = request.weightKg();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_WEIGHT", "Parcel weight must be greater than 0 kg");
        }

        // 2. Resolve Pricing Rule
        PricingRule rule = resolvePricingRule(request.ruleCode());

        // Validate weight against rule limits
        if (rule.getMinWeightKg() != null && weight.compareTo(rule.getMinWeightKg()) < 0) {
            throw ApiException.badRequest("WEIGHT_BELOW_MINIMUM",
                    String.format("Parcel weight %.2f kg is below minimum allowable weight of %.2f kg",
                            weight, rule.getMinWeightKg()));
        }
        if (rule.getMaxWeightKg() != null && weight.compareTo(rule.getMaxWeightKg()) > 0) {
            throw ApiException.badRequest("WEIGHT_EXCEEDS_MAXIMUM",
                    String.format("Parcel weight %.2f kg exceeds maximum allowable weight limit of %.2f kg",
                            weight, rule.getMaxWeightKg()));
        }

        // 3. Resolve Zone Charge
        String effectiveZoneCode;
        BigDecimal zoneCharge;

        if (request.customZoneCharge() != null) {
            zoneCharge = request.customZoneCharge().setScale(2, RoundingMode.HALF_UP);
            effectiveZoneCode = request.zoneCode() != null ? request.zoneCode().trim().toUpperCase() : "CUSTOM";
        } else if (request.zoneCode() != null && !request.zoneCode().isBlank()) {
            final String zoneCodeLookup = request.zoneCode().trim().toUpperCase();
            effectiveZoneCode = zoneCodeLookup;
            DeliveryZone zone = zoneRepository.findByCodeIgnoreCase(zoneCodeLookup)
                    .orElseThrow(() -> ApiException.badRequest("INVALID_ZONE",
                            "Invalid or unrecognized delivery zone: " + zoneCodeLookup));

            if (zone.getStatus() == ZoneStatus.INACTIVE) {
                throw ApiException.badRequest("INACTIVE_ZONE",
                        "Delivery zone is currently inactive: " + zoneCodeLookup);
            }
            zoneCharge = zone.getBaseCharge() != null ? zone.getBaseCharge() : rule.getDefaultZoneCharge();
        } else {
            // Default zone charge defined in the pricing rule
            effectiveZoneCode = "STANDARD_ZONE";
            zoneCharge = rule.getDefaultZoneCharge();
        }
        zoneCharge = zoneCharge.setScale(2, RoundingMode.HALF_UP);

        // 4. Calculate Weight Charge: Weight (kg) * Rate per kg
        BigDecimal baseFee = rule.getBaseFee().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ratePerKg = rule.getRatePerKg().setScale(2, RoundingMode.HALF_UP);
        BigDecimal weightCharge = weight.multiply(ratePerKg).setScale(2, RoundingMode.HALF_UP);

        // 5. Urgent Delivery Surcharge (Disabled by default, applied only if requested AND enabled in rule)
        boolean urgentRequested = Boolean.TRUE.equals(request.isUrgent());
        boolean urgentApplied = false;
        BigDecimal urgentSurcharge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (urgentRequested && rule.isUrgentSurchargeEnabled()) {
            urgentSurcharge = rule.getUrgentSurcharge().setScale(2, RoundingMode.HALF_UP);
            urgentApplied = true;
        }

        // 6. Total Delivery Fee = Base Fee + Weight Charge + Zone Charge + Urgent Surcharge
        BigDecimal totalDeliveryFee = baseFee
                .add(weightCharge)
                .add(zoneCharge)
                .add(urgentSurcharge)
                .setScale(2, RoundingMode.HALF_UP);

        String breakdownDescription = String.format(
                "Base Fee (%s %.2f) + Weight Charge (%.2f kg @ %s %.2f/kg = %s %.2f) + Zone Charge [%s] (%s %.2f)%s = %s %.2f",
                DEFAULT_CURRENCY, baseFee,
                weight, DEFAULT_CURRENCY, ratePerKg, DEFAULT_CURRENCY, weightCharge,
                effectiveZoneCode, DEFAULT_CURRENCY, zoneCharge,
                urgentApplied ? String.format(" + Urgent Surcharge (%s %.2f)", DEFAULT_CURRENCY, urgentSurcharge) : "",
                DEFAULT_CURRENCY, totalDeliveryFee
        );

        log.info("Calculated fee: total={}, rule={}, zone={}", totalDeliveryFee, rule.getRuleCode(), effectiveZoneCode);

        return new PricingResponse(
                totalDeliveryFee,
                baseFee,
                weightCharge,
                weight.setScale(2, RoundingMode.HALF_UP),
                ratePerKg,
                zoneCharge,
                effectiveZoneCode,
                urgentSurcharge,
                urgentApplied,
                DEFAULT_CURRENCY,
                rule.getRuleCode(),
                breakdownDescription
        );
    }

    /**
     * Retrieve all pricing rules.
     */
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> getAllPricingRules() {
        return pricingRuleRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(PricingRuleResponse::from)
                .toList();
    }

    /**
     * Retrieve a specific pricing rule by ID.
     */
    @Transactional(readOnly = true)
    public PricingRuleResponse getPricingRuleById(Long id) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found with ID: " + id));
        return PricingRuleResponse.from(rule);
    }

    /**
     * Create a new pricing rule.
     */
    public PricingRuleResponse createPricingRule(PricingRuleRequest request) {
        String code = request.ruleCode().trim().toUpperCase();
        if (pricingRuleRepository.existsByRuleCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("DUPLICATE_RULE_CODE",
                    "A pricing rule already exists with code: " + code);
        }

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            clearDefaultOnOtherRules(null);
        }

        PricingRule rule = new PricingRule();
        rule.setRuleName(request.ruleName().trim());
        rule.setRuleCode(code);
        rule.setDescription(request.description());
        rule.setBaseFee(request.baseFee());
        rule.setRatePerKg(request.ratePerKg());
        rule.setDefaultZoneCharge(request.defaultZoneCharge());
        rule.setUrgentSurcharge(request.urgentSurcharge() != null ? request.urgentSurcharge() : new BigDecimal("200.00"));
        rule.setUrgentSurchargeEnabled(Boolean.TRUE.equals(request.urgentSurchargeEnabled()));
        rule.setMinWeightKg(request.minWeightKg() != null ? request.minWeightKg() : new BigDecimal("0.01"));
        rule.setMaxWeightKg(request.maxWeightKg());
        rule.setActive(request.active() == null || request.active());
        rule.setDefault(isDefault);
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());

        PricingRule saved = pricingRuleRepository.save(rule);
        log.info("Created pricing rule: id={}, code={}", saved.getId(), saved.getRuleCode());
        return PricingRuleResponse.from(saved);
    }

    /**
     * Update an existing pricing rule.
     */
    public PricingRuleResponse updatePricingRule(Long id, PricingRuleRequest request) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found with ID: " + id));

        String newCode = request.ruleCode().trim().toUpperCase();
        if (pricingRuleRepository.existsByRuleCodeIgnoreCaseAndIdNot(newCode, id)) {
            throw new DuplicateResourceException("DUPLICATE_RULE_CODE",
                    "Another pricing rule already exists with code: " + newCode);
        }

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            clearDefaultOnOtherRules(id);
        }

        rule.setRuleName(request.ruleName().trim());
        rule.setRuleCode(newCode);
        rule.setDescription(request.description());
        rule.setBaseFee(request.baseFee());
        rule.setRatePerKg(request.ratePerKg());
        rule.setDefaultZoneCharge(request.defaultZoneCharge());
        if (request.urgentSurcharge() != null) {
            rule.setUrgentSurcharge(request.urgentSurcharge());
        }
        if (request.urgentSurchargeEnabled() != null) {
            rule.setUrgentSurchargeEnabled(request.urgentSurchargeEnabled());
        }
        if (request.minWeightKg() != null) {
            rule.setMinWeightKg(request.minWeightKg());
        }
        rule.setMaxWeightKg(request.maxWeightKg());
        if (request.active() != null) {
            rule.setActive(request.active());
        }
        rule.setDefault(isDefault);
        rule.setUpdatedAt(OffsetDateTime.now());

        PricingRule updated = pricingRuleRepository.save(rule);
        log.info("Updated pricing rule: id={}, code={}", updated.getId(), updated.getRuleCode());
        return PricingRuleResponse.from(updated);
    }

    private PricingRule resolvePricingRule(String ruleCode) {
        if (ruleCode != null && !ruleCode.isBlank()) {
            PricingRule rule = pricingRuleRepository.findByRuleCodeIgnoreCase(ruleCode.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found for code: " + ruleCode.trim()));
            if (!rule.isActive()) {
                throw ApiException.badRequest("INACTIVE_PRICING_RULE",
                        "The specified pricing rule is inactive: " + ruleCode.trim());
            }
            return rule;
        }

        // Fall back to default active rule, or any active rule
        return pricingRuleRepository.findFirstByIsDefaultTrueAndActiveTrue()
                .or(pricingRuleRepository::findFirstByActiveTrueOrderByCreatedAtAsc)
                .orElseThrow(() -> new ResourceNotFoundException("No active pricing rule configured in the system"));
    }

    private void clearDefaultOnOtherRules(Long excludeId) {
        pricingRuleRepository.clearDefaultRules(excludeId);
    }
}
