-- Unknown external totals are not zero and cannot establish a match.
ALTER TABLE finance_reconciliations ALTER COLUMN provider_collected_amount DROP NOT NULL;
ALTER TABLE finance_reconciliations ALTER COLUMN provider_collected_amount DROP DEFAULT;
ALTER TABLE finance_reconciliations ALTER COLUMN discrepancy_amount DROP NOT NULL;
ALTER TABLE finance_reconciliations ALTER COLUMN discrepancy_amount DROP DEFAULT;
ALTER TABLE finance_reconciliations ALTER COLUMN status SET DEFAULT 'PROVIDER_UNAVAILABLE';
-- Existing automatically matched records were based on the local ledger, not provider evidence.
-- Keep administrator-resolved records and their audit trail intact.
UPDATE finance_reconciliations
SET status = 'PROVIDER_UNAVAILABLE', provider_collected_amount = NULL,
    discrepancy_amount = NULL, updated_at = NOW(), version = version + 1
WHERE status = 'MATCHED';
