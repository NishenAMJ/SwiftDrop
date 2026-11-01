package com.swiftdrop.identity.apikey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<ApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    @Query("""
        select count(k) from ApiKey k
        where k.merchantId = :merchantId and k.revokedAt is null and (k.expiresAt is null or k.expiresAt > :now)
        """)
    long countActive(@Param("merchantId") UUID merchantId, @Param("now") Instant now);
}
