package com.swiftdrop.payment.settlement.repository;

import com.swiftdrop.payment.settlement.entity.MerchantSettlement;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MerchantSettlementRepository extends JpaRepository<MerchantSettlement, UUID> {
    Optional<MerchantSettlement> findByCalculatedByAndIdempotencyKey(UUID actorId, String key);
    Optional<MerchantSettlement> findByApprovedByAndApprovalIdempotencyKey(UUID actorId, String key);
    Optional<MerchantSettlement> findByPaidByAndPaymentIdempotencyKey(UUID actorId, String key);
    Page<MerchantSettlement> findByMerchantId(UUID merchantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MerchantSettlement s where s.id = :id")
    Optional<MerchantSettlement> lock(@Param("id") UUID id);
}
