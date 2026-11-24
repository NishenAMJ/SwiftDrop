package com.swiftdrop.payment.settlement.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "merchant_settlement_entries", schema = "payment_settlement")
@IdClass(MerchantSettlementEntry.Key.class)
public class MerchantSettlementEntry {
    @Id
    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Id
    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    public UUID getSettlementId() { return settlementId; }
    public void setSettlementId(UUID settlementId) { this.settlementId = settlementId; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public void setLedgerEntryId(UUID ledgerEntryId) { this.ledgerEntryId = ledgerEntryId; }

    public static final class Key implements Serializable {
        private UUID settlementId;
        private UUID ledgerEntryId;

        public Key() { }
        public Key(UUID settlementId, UUID ledgerEntryId) {
            this.settlementId = settlementId;
            this.ledgerEntryId = ledgerEntryId;
        }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Key other)) return false;
            return Objects.equals(settlementId, other.settlementId)
                && Objects.equals(ledgerEntryId, other.ledgerEntryId);
        }
        @Override public int hashCode() { return Objects.hash(settlementId, ledgerEntryId); }
    }
}
