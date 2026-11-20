package com.swiftdrop.payment.reconciliation.repository;

import com.swiftdrop.payment.reconciliation.entity.CashReconciliation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashReconciliationRepository extends JpaRepository<CashReconciliation, UUID> {
    Optional<CashReconciliation> findByHandoverId(UUID handoverId);
    Optional<CashReconciliation> findByConfirmedByAndConfirmationIdempotencyKey(UUID actorId, String key);
}
