package com.swiftdrop.payment.ledger.repository;

import com.swiftdrop.payment.ledger.entity.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MerchantLedgerEntryRepository extends JpaRepository<MerchantLedgerEntry, UUID> {
    Optional<MerchantLedgerEntry> findByIdempotencyKey(String key);
    Optional<MerchantLedgerEntry> findByEntryTypeAndSourceReference(LedgerEntryType type, String sourceReference);
    Page<MerchantLedgerEntry> findByMerchantId(UUID merchantId, Pageable pageable);
    Page<MerchantLedgerEntry> findByMerchantIdAndCurrency(UUID merchantId, String currency, Pageable pageable);

    @Query("select coalesce(sum(e.amount), 0) from MerchantLedgerEntry e "
        + "where e.merchantId = :merchantId and e.currency = :currency")
    java.math.BigDecimal balance(@Param("merchantId") UUID merchantId,
        @Param("currency") String currency);

    @Query("select e from MerchantLedgerEntry e where e.merchantId = :merchantId "
        + "and e.currency = :currency and e.createdAt <= :cutoff and e.settlementId is null "
        + "and e.settlementEligible = true "
        + "and not exists (select se from MerchantSettlementEntry se where se.ledgerEntryId = e.id) "
        + "order by e.createdAt, e.id")
    List<MerchantLedgerEntry> findUnsettledThrough(@Param("merchantId") UUID merchantId,
        @Param("currency") String currency, @Param("cutoff") LocalDateTime cutoff);
}
