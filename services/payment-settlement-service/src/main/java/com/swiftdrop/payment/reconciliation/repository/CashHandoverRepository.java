package com.swiftdrop.payment.reconciliation.repository;

import com.swiftdrop.payment.reconciliation.entity.CashHandover;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CashHandoverRepository extends JpaRepository<CashHandover, UUID> {
    Optional<CashHandover> findByCourierIdAndIdempotencyKey(UUID courierId, String key);
    Page<CashHandover> findByCourierId(UUID courierId, Pageable pageable);
    Page<CashHandover> findByCourierIdAndStatus(UUID courierId,
        com.swiftdrop.payment.reconciliation.entity.CashHandoverStatus status, Pageable pageable);
    Page<CashHandover> findByStatus(
        com.swiftdrop.payment.reconciliation.entity.CashHandoverStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from CashHandover h where h.id = :id")
    Optional<CashHandover> lock(@Param("id") UUID id);
}
