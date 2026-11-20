package com.swiftdrop.payment.ledger.service;

import com.swiftdrop.payment.ledger.dto.MerchantLedgerDtos;
import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantLedgerServiceTest {
    private final MerchantLedgerEntryRepository entries = mock(MerchantLedgerEntryRepository.class);
    private final MerchantLedgerService service = new MerchantLedgerService(entries);
    private final UUID merchantId = UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    private final Actor merchant = new Actor(UUID.randomUUID(), merchantId, Set.of("MERCHANT"), "test");
    private final Actor dispatcher = new Actor(UUID.randomUUID(), null, Set.of("DISPATCHER"), "test");

    @Test
    void merchantCanReadOnlyOwnLedger() {
        when(entries.findByMerchantIdAndCurrency(eq(merchantId), eq("LKR"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(entries.balance(merchantId, "LKR")).thenReturn(new BigDecimal("1250.00"));

        MerchantLedgerDtos.Page page = service.list(null, "LKR", 0, 20, merchant);
        assertEquals(new BigDecimal("1250.00"), page.balance());
        assertEquals("LKR", page.currency());

        PaymentProblem forbidden = assertThrows(PaymentProblem.class,
            () -> service.list(UUID.randomUUID(), "LKR", 0, 20, merchant));
        assertEquals(403, forbidden.status);
    }

    @Test
    void dispatcherAdjustmentIsAuditedAndSettlementEligible() {
        when(entries.findByIdempotencyKey("adjustment-key-001")).thenReturn(Optional.empty());
        when(entries.findByEntryTypeAndSourceReference(LedgerEntryType.ADJUSTMENT, "case-123"))
            .thenReturn(Optional.empty());
        when(entries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MerchantLedgerDtos.Entry result = service.adjust(new MerchantLedgerDtos.Adjustment(
            merchantId, new BigDecimal("-100.00"), "LKR", "case-123", "Delivery charge correction"),
            "adjustment-key-001", dispatcher);

        assertEquals("ADJUSTMENT", result.entryType());
        assertEquals(new BigDecimal("-100.00"), result.amount());
        assertTrue(result.settlementEligible());
        assertEquals("Delivery charge correction", result.reason());
    }
}
