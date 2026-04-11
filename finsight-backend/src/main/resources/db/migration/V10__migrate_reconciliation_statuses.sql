-- V10__migrate_reconciliation_statuses.sql
-- Migration to sync the deprecated 'reconciled' boolean with the new 'reconciliation_status' enum,
-- and set default status for rows where it is missing.

-- 1. For transactions marked as reconciled (reconciled = 1) but reconciliation_status not MATCHED,
--    update to MATCHED.
UPDATE bank_transactions
SET reconciliation_status = 'MATCHED'
WHERE reconciled = 1 AND reconciliation_status != 'MATCHED';

-- 2. For transactions not reconciled (reconciled = 0) and reconciliation_status is NULL,
--    set to PENDING (the new default).
UPDATE bank_transactions
SET reconciliation_status = 'PENDING'
WHERE reconciled = 0 AND reconciliation_status IS NULL;

-- 3. (Optional) If there are rows with reconciliation_status = 'MANUAL_REVIEW' that were
--    created before the default changed, we leave them as MANUAL_REVIEW – they are already
--    flagged for human review.

-- Note: The 'reconciled' column remains deprecated and will be removed in a future schema version.
-- New code should rely solely on reconciliation_status.