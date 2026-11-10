package com.swiftdrop.delivery.domain;

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
@Table(name = "warehouse_parcels")
public class WarehouseParcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_reference", nullable = false, unique = true, length = 50)
    private String parcelReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseParcelState state;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "arrival_notes", length = 500)
    private String arrivalNotes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WarehouseParcel() {
    }

    public WarehouseParcel(String parcelReference, Warehouse warehouse, Instant arrivedAt, String arrivalNotes) {
        this.parcelReference = parcelReference;
        this.warehouse = warehouse;
        this.arrivedAt = arrivedAt;
        this.arrivalNotes = arrivalNotes;
        this.state = WarehouseParcelState.ARRIVED;
    }

    public void markSorted() {
        state = WarehouseParcelState.SORTED;
    }

    public void markInTransfer() {
        state = WarehouseParcelState.IN_TRANSFER;
    }

    public void receiveAt(Warehouse destination, Instant receivedAt) {
        warehouse = destination;
        state = WarehouseParcelState.RECEIVED;
        arrivedAt = receivedAt;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getParcelReference() { return parcelReference; }
    public Warehouse getWarehouse() { return warehouse; }
    public WarehouseParcelState getState() { return state; }
    public Instant getArrivedAt() { return arrivedAt; }
    public String getArrivalNotes() { return arrivalNotes; }
    public Instant getUpdatedAt() { return updatedAt; }
}
