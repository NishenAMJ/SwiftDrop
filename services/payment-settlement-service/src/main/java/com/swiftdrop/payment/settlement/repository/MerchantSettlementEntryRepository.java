package com.swiftdrop.payment.settlement.repository;

import com.swiftdrop.payment.settlement.entity.MerchantSettlementEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantSettlementEntryRepository
        extends JpaRepository<MerchantSettlementEntry, MerchantSettlementEntry.Key> {
    List<MerchantSettlementEntry> findBySettlementId(UUID settlementId);
    boolean existsByLedgerEntryId(UUID ledgerEntryId);
}
