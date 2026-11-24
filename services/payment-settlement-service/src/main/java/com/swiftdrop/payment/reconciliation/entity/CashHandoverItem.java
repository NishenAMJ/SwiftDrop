package com.swiftdrop.payment.reconciliation.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cash_handover_items", schema = "payment_settlement")
@IdClass(CashHandoverItem.Key.class)
public class CashHandoverItem {
    @Id
    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;

    @Id
    @Column(name = "cod_collection_id", nullable = false)
    private UUID codCollectionId;

    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID handoverId) { this.handoverId = handoverId; }
    public UUID getCodCollectionId() { return codCollectionId; }
    public void setCodCollectionId(UUID codCollectionId) { this.codCollectionId = codCollectionId; }

    public static final class Key implements Serializable {
        private UUID handoverId;
        private UUID codCollectionId;

        public Key() { }
        public Key(UUID handoverId, UUID codCollectionId) {
            this.handoverId = handoverId;
            this.codCollectionId = codCollectionId;
        }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Key other)) return false;
            return Objects.equals(handoverId, other.handoverId)
                && Objects.equals(codCollectionId, other.codCollectionId);
        }
        @Override public int hashCode() { return Objects.hash(handoverId, codCollectionId); }
    }
}
