-- Pricing snapshot identifiers are opaque upstream values (for example, SNP-1234ABCD).
-- Preserve existing UUID values while allowing the Payment service to store the full contract.
ALTER TABLE payment_settlement.payments
    ALTER COLUMN pricing_snapshot_id TYPE VARCHAR(64)
    USING pricing_snapshot_id::text;
