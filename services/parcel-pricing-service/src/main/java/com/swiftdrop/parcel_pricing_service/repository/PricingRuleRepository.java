package com.swiftdrop.parcel_pricing_service.repository;

import com.swiftdrop.parcel_pricing_service.entity.PricingRule;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    Optional<PricingRule> findByRuleCodeIgnoreCase(String ruleCode);

    Optional<PricingRule> findFirstByIsDefaultTrueAndActiveTrue();

    Optional<PricingRule> findFirstByActiveTrueOrderByCreatedAtAsc();

    boolean existsByRuleCodeIgnoreCase(String ruleCode);

    boolean existsByRuleCodeIgnoreCaseAndIdNot(String ruleCode, Long id);

    List<PricingRule> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("""
            UPDATE PricingRule r
            SET r.isDefault = false
            WHERE r.isDefault = true
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    int clearDefaultRules(@Param("excludeId") Long excludeId);
}
