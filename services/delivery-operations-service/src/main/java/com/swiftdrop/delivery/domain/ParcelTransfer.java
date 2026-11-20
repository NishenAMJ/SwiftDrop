package com.swiftdrop.delivery.domain;

import com.swiftdrop.delivery.exception.InvalidOperationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel_transfers")
public class ParcelTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_reference", nullable = false, length = 50)
    private String parcelReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferStatus status;

    @Column(name = "transfer_date")
    private Instant transferDate;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_by", length = 120)
    private String receivedBy;

    @Column(name = "receiving_notes", length = 500)
    private String receivingNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ParcelTransfer() {
    }

    public ParcelTransfer(String parcelReference, Warehouse sourceWarehouse, Warehouse destinationWarehouse) {
        this.parcelReference = parcelReference;
        this.sourceWarehouse = sourceWarehouse;
        this.destinationWarehouse = destinationWarehouse;
        this.status = TransferStatus.CREATED;
    }

    public void dispatch(Instant date) {
        if (status != TransferStatus.CREATED) {
            throw new InvalidOperationException("Only a CREATED transfer can be dispatched");
        }
        status = TransferStatus.IN_TRANSIT;
        transferDate = date;
    }

    public void receive(Instant date, String receiver, String notes) {
        if (status != TransferStatus.IN_TRANSIT) {
            throw new InvalidOperationException("Only an IN_TRANSIT transfer can be received");
        }
        status = TransferStatus.RECEIVED;
        receivedAt = date;
        receivedBy = receiver;
        receivingNotes = notes;
    }

    public void cancel() {
        if (status != TransferStatus.CREATED) {
            throw new InvalidOperationException("Only a CREATED transfer can be cancelled");
        }
        status = TransferStatus.CANCELLED;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getParcelReference() { return parcelReference; }
    public Warehouse getSourceWarehouse() { return sourceWarehouse; }
    public Warehouse getDestinationWarehouse() { return destinationWarehouse; }
    public TransferStatus getStatus() { return status; }
    public Instant getTransferDate() { return transferDate; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getReceivedBy() { return receivedBy; }
    public String getReceivingNotes() { return receivingNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
