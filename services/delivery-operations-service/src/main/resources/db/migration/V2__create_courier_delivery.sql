CREATE TABLE courier_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(300),
    max_weight_kg NUMERIC(8, 2),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    identity_user_id UUID NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    vehicle_number VARCHAR(30),
    category_id UUID NOT NULL REFERENCES courier_categories(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE delivery_assignments (
    id UUID PRIMARY KEY,
    -- parcel_id is the Parcel & Pricing identifier; parcel_reference is its public tracking number.
    parcel_id UUID NOT NULL,
    parcel_reference VARCHAR(50) NOT NULL,
    courier_id UUID NOT NULL REFERENCES couriers(id),
    assigned_by VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(500),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    picked_up_at TIMESTAMP WITH TIME ZONE,
    out_for_delivery_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES delivery_assignments(id),
    parcel_reference VARCHAR(50) NOT NULL,
    attempt_number INTEGER NOT NULL,
    reason VARCHAR(40) NOT NULL,
    notes VARCHAR(500),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE proof_of_delivery (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL UNIQUE REFERENCES delivery_assignments(id),
    parcel_reference VARCHAR(50) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    notes VARCHAR(500),
    media_url VARCHAR(500),
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE delivery_issues (
    id UUID PRIMARY KEY,
    courier_id UUID NOT NULL REFERENCES couriers(id),
    assignment_id UUID REFERENCES delivery_assignments(id),
    parcel_reference VARCHAR(50),
    issue_type VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_by VARCHAR(120),
    resolution_notes VARCHAR(1000),
    reviewed_at TIMESTAMP WITH TIME ZONE
);

-- Transactional outbox: events are written with the change that caused them and published afterwards.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(next_attempt_at) WHERE published_at IS NULL;
CREATE INDEX idx_couriers_category ON couriers(category_id);
CREATE INDEX idx_assignments_courier_status ON delivery_assignments(courier_id, status);
CREATE INDEX idx_assignments_parcel_reference ON delivery_assignments(parcel_reference);
CREATE UNIQUE INDEX uk_assignments_one_active_per_parcel ON delivery_assignments(parcel_reference)
    WHERE status NOT IN ('DELIVERED', 'DELIVERY_FAILED');
CREATE INDEX idx_attempts_parcel_reference ON delivery_attempts(parcel_reference);
CREATE INDEX idx_attempts_assignment ON delivery_attempts(assignment_id);
CREATE INDEX idx_issues_courier ON delivery_issues(courier_id);
CREATE INDEX idx_issues_status ON delivery_issues(status);

INSERT INTO courier_categories (id, code, name, description, max_weight_kg, status, created_at, updated_at) VALUES
    (gen_random_uuid(), 'MOTORCYCLE', 'Motorcycle', 'Small parcels and documents in urban areas', 20.00, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'THREE_WHEELER', 'Three-wheeler', 'Medium parcels and short-distance deliveries', 100.00, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'VAN', 'Van', 'Large or bulk parcels', 1000.00, 'ACTIVE', now(), now());
