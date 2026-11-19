CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    address_line_1 VARCHAR(160) NOT NULL,
    address_line_2 VARCHAR(160),
    city VARCHAR(80) NOT NULL,
    district VARCHAR(80) NOT NULL,
    postal_code VARCHAR(20),
    contact_name VARCHAR(120) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    contact_email VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE warehouse_parcels (
    id UUID PRIMARY KEY,
    parcel_reference VARCHAR(50) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    state VARCHAR(30) NOT NULL,
    arrived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_notes VARCHAR(500),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_warehouse_parcel_reference UNIQUE (parcel_reference)
);

CREATE TABLE sorting_records (
    id UUID PRIMARY KEY,
    parcel_reference VARCHAR(50) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    next_warehouse_id UUID REFERENCES warehouses(id),
    destination VARCHAR(160) NOT NULL,
    notes VARCHAR(500),
    sorted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE parcel_transfers (
    id UUID PRIMARY KEY,
    parcel_reference VARCHAR(50) NOT NULL,
    source_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(30) NOT NULL,
    transfer_date TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    received_by VARCHAR(120),
    receiving_notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_different_warehouses CHECK (source_warehouse_id <> destination_warehouse_id)
);

CREATE INDEX idx_warehouse_parcels_warehouse ON warehouse_parcels(warehouse_id);
CREATE INDEX idx_sorting_records_warehouse ON sorting_records(warehouse_id);
CREATE INDEX idx_transfers_source_status ON parcel_transfers(source_warehouse_id, status);
CREATE INDEX idx_transfers_destination_status ON parcel_transfers(destination_warehouse_id, status);
CREATE INDEX idx_transfers_parcel_reference ON parcel_transfers(parcel_reference);
