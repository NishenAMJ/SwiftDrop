package com.swiftdrop.payment.reconciliation.repository;

import com.swiftdrop.payment.reconciliation.entity.CashHandoverItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashHandoverItemRepository
        extends JpaRepository<CashHandoverItem, CashHandoverItem.Key> {
    List<CashHandoverItem> findByHandoverId(UUID handoverId);
    boolean existsByCodCollectionId(UUID codCollectionId);
}
