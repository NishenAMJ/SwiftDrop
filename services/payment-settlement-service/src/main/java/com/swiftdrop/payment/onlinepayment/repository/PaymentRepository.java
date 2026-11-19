package com.swiftdrop.payment.onlinepayment.repository;

import com.swiftdrop.payment.onlinepayment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByInitiatedByAndIdempotencyKey(UUID actor, String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lock(@Param("id") UUID id);
}
