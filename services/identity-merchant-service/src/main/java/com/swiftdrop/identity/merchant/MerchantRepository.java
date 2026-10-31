package com.swiftdrop.identity.merchant;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID>, JpaSpecificationExecutor<Merchant> {

    Optional<Merchant> findByUserId(UUID userId);

    /** Locks the merchant row, so concurrent API-key creation can't exceed the limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Merchant m where m.userId = :userId")
    Optional<Merchant> lockByUserId(@Param("userId") UUID userId);
}
