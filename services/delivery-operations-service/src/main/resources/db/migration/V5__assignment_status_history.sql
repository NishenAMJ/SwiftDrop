-- Audit trail: every status change of an assignment, with who made it. The actor is the signed-in user's
-- identity id (the access token's sub) and role, never something the caller types in. Rows are only ever
-- added. changed_by is null only for changes made outside a request (there are none in normal use).
CREATE TABLE assignment_status_history (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES delivery_assignments(id),
    parcel_reference VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by UUID,
    changed_by_role VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_assignment_status_history_assignment
    ON assignment_status_history (assignment_id, changed_at);
