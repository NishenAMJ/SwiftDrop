-- Preserve accounts; fail rather than guess when legacy roles/applications conflict.
DO $$
DECLARE conflicts text;
BEGIN
    SELECT string_agg(user_id::text, ', ') INTO conflicts FROM (
        SELECT user_id FROM (
            SELECT ur.user_id, r.name AS role FROM identity_merchant.user_roles ur
            JOIN identity_merchant.roles r USING (role_id) WHERE r.name <> 'CUSTOMER'
            UNION SELECT user_id, 'MERCHANT' FROM identity_merchant.merchants
            UNION SELECT user_id, 'COURIER' FROM identity_merchant.couriers
        ) candidates GROUP BY user_id HAVING count(DISTINCT role) > 1
    ) ambiguous;
    IF conflicts IS NOT NULL THEN
        RAISE EXCEPTION 'Single-role migration: resolve conflicting roles/applications for user IDs: %', conflicts;
    END IF;
END $$;

ALTER TABLE identity_merchant.users ADD COLUMN account_role varchar(20);
UPDATE identity_merchant.users u SET account_role = COALESCE(
    (SELECT r.name FROM identity_merchant.user_roles ur JOIN identity_merchant.roles r USING(role_id)
     WHERE ur.user_id = u.user_id AND r.name = 'DISPATCHER'),
    (SELECT 'MERCHANT' FROM identity_merchant.merchants m WHERE m.user_id = u.user_id),
    (SELECT 'COURIER' FROM identity_merchant.couriers c WHERE c.user_id = u.user_id),
    (SELECT r.name FROM identity_merchant.user_roles ur JOIN identity_merchant.roles r USING(role_id)
     WHERE ur.user_id = u.user_id AND r.name <> 'CUSTOMER'), 'CUSTOMER');
ALTER TABLE identity_merchant.users ADD CONSTRAINT chk_account_role
    CHECK (account_role IN ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER'));
ALTER TABLE identity_merchant.users ADD CONSTRAINT chk_role_selection_google
    CHECK (account_role IS NOT NULL OR google_subject IS NOT NULL);

DELETE FROM identity_merchant.user_roles;
INSERT INTO identity_merchant.user_roles(user_id, role_id)
SELECT u.user_id, r.role_id FROM identity_merchant.users u
JOIN identity_merchant.roles r ON r.name = u.account_role
WHERE u.account_role IN ('CUSTOMER', 'DISPATCHER')
OR (u.account_role = 'MERCHANT' AND EXISTS (
    SELECT 1 FROM identity_merchant.merchants m WHERE m.user_id = u.user_id AND m.status = 'APPROVED'))
OR (u.account_role = 'COURIER' AND EXISTS (
    SELECT 1 FROM identity_merchant.couriers c WHERE c.user_id = u.user_id AND c.status = 'APPROVED'));
ALTER TABLE identity_merchant.user_roles ADD CONSTRAINT uq_user_single_active_role UNIQUE(user_id);

CREATE FUNCTION identity_merchant.enforce_account_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.account_role IS NOT NULL AND NEW.account_role IS DISTINCT FROM OLD.account_role THEN
        RAISE EXCEPTION 'Assigned account role cannot be changed';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_account_role BEFORE UPDATE ON identity_merchant.users
FOR EACH ROW EXECUTE FUNCTION identity_merchant.enforce_account_role();

CREATE FUNCTION identity_merchant.enforce_active_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM identity_merchant.users u JOIN identity_merchant.roles r
        ON r.name = u.account_role WHERE u.user_id = NEW.user_id AND r.role_id = NEW.role_id) THEN
        RAISE EXCEPTION 'Active role must match assigned account role';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER matching_active_role BEFORE INSERT OR UPDATE ON identity_merchant.user_roles
FOR EACH ROW EXECUTE FUNCTION identity_merchant.enforce_active_role();

-- Updated JWT validators reject access tokens without identity_version=2.
UPDATE identity_merchant.refresh_tokens SET revoked_at = now() WHERE revoked_at IS NULL;
