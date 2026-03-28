-- Flyway Migration V4: Relax the CHECK constraint on reconciliation_status
-- SQLite doesn't allow altering constraints, so we recreate the table structure.
-- This script relaxes the constraint to allow 'PENDING', 'UNMATCHED', etc.

PRAGMA foreign_keys=OFF;

-- 1. Create the new table without the restrictive CHECK constraint
CREATE TABLE bank_transactions_new (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id        TEXT    NOT NULL DEFAULT 'local_tenant',
    tx_date          DATE    NOT NULL,
    description      TEXT    NOT NULL,
    type             TEXT    NOT NULL CHECK (type IN ('CREDIT','DEBIT')),
    amount           REAL    NOT NULL,
    category_id      INTEGER REFERENCES categories(id),
    reconciled       INTEGER NOT NULL DEFAULT 0,
    receipt_id       INTEGER REFERENCES receipts(id),
    reference_number TEXT,
    created_at       DATETIME NOT NULL DEFAULT (datetime('now')),
    vendor_normalized TEXT,
    vendor_type      TEXT,
    block            TEXT,
    floor            TEXT,
    flat_number      TEXT,
    sub_category     TEXT,
    account_type     VARCHAR(50) DEFAULT 'MAINTENANCE',
    audit_log        TEXT,
    is_manual_override boolean,
    match_score      float,
    match_type       varchar(255),
    matched_receipt_ids varchar(255),
    reconciliation_status varchar(255), -- The fix: Restrictive CHECK removed
    vendor           varchar(255),
    ai_reasoning     TEXT,
    confidence_score float,
    is_duplicate     boolean,
    original_snippet TEXT,
    status           varchar(255)
);

-- 2. Migrating data
-- We must explicitly list the columns to be safe
INSERT INTO bank_transactions_new (
    id, tenant_id, tx_date, description, type, amount, category_id, reconciled, receipt_id, reference_number, created_at,
    vendor_normalized, vendor_type, block, floor, flat_number, sub_category, account_type, audit_log, is_manual_override,
    match_score, match_type, matched_receipt_ids, reconciliation_status, vendor, ai_reasoning, confidence_score,
    is_duplicate, original_snippet, status
)
SELECT 
    id, tenant_id, tx_date, description, type, amount, category_id, reconciled, receipt_id, reference_number, created_at,
    vendor_normalized, vendor_type, block, floor, flat_number, sub_category, account_type, audit_log, is_manual_override,
    match_score, match_type, matched_receipt_ids, reconciliation_status, vendor, ai_reasoning, confidence_score,
    is_duplicate, original_snippet, status
FROM bank_transactions;

-- 3. Swapping tables
DROP TABLE bank_transactions;
ALTER TABLE bank_transactions_new RENAME TO bank_transactions;

-- 4. Re-creating indexes
CREATE INDEX idx_bank_tx_tenant   ON bank_transactions (tenant_id);
CREATE INDEX idx_bank_tx_date     ON bank_transactions (tx_date);
CREATE INDEX idx_bank_tx_reconciled ON bank_transactions (reconciled);

PRAGMA foreign_keys=ON;
