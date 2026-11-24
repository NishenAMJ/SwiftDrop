-- A cancelled assignment is finished, like a delivered or failed one, so it must not count as the one
-- active assignment a parcel is allowed to have.
DROP INDEX uk_assignments_one_active_per_parcel;
CREATE UNIQUE INDEX uk_assignments_one_active_per_parcel ON delivery_assignments(parcel_reference)
    WHERE status NOT IN ('DELIVERED', 'DELIVERY_FAILED', 'CANCELLED');
