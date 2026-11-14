package com.swiftdrop.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sorting_records")
public class SortingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_reference", nullable = false, length = 50)
    private String parcelReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_warehouse_id")
    private Warehouse nextWarehouse;

    @Column(nullable = false, length = 160)
    private String destination;

    @Column(length = 500)
    private String notes;

    @Column(name = "sorted_at", nullable = false)
    private Instant sortedAt;

    protected SortingRecord() {
    }

    public SortingRecord(String parcelReference, Warehouse warehouse, Warehouse nextWarehouse,
                         String destination, String notes, Instant sortedAt) {
        this.parcelReference = parcelReference;
        this.warehouse = warehouse;
        this.nextWarehouse = nextWarehouse;
        this.destination = destination;
        this.notes = notes;
        this.sortedAt = sortedAt;
    }

    public UUID getId() { return id; }
    public String getParcelReference() { return parcelReference; }
    public Warehouse getWarehouse() { return warehouse; }
    public Warehouse getNextWarehouse() { return nextWarehouse; }
    public String getDestination() { return destination; }
    public String getNotes() { return notes; }
    public Instant getSortedAt() { return sortedAt; }
}
