-- FinSight: Flyway Migration V1 - Initial Schema
-- This migration creates the core database schema for FinSight.
-- Multi-tenancy ready: every table includes tenant_id.

-- Enable WAL mode for better concurrency (SQLite specific)
PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
PRAGMA busy_timeout=5000;
PRAGMA foreign_keys=ON;

-- App Configuration table
CREATE TABLE IF NOT EXISTS app_config (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id        TEXT    NOT NULL DEFAULT 'local_tenant',
    apartment_name   TEXT    NOT NULL DEFAULT 'My Apartment',
    gemini_api_key   TEXT,
    drive_folder_url TEXT,
    ocr_mode         TEXT    NOT NULL DEFAULT 'MODE_LOW_COST',
    theme_preference TEXT    NOT NULL DEFAULT 'DARK',
    UNIQUE (tenant_id)
);

-- Categories table
CREATE TABLE IF NOT EXISTS categories (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id TEXT    NOT NULL DEFAULT 'local_tenant',
    name      TEXT    NOT NULL,
    type      TEXT    NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    UNIQUE (tenant_id, name)
);

-- Receipts table (from Google Drive)
CREATE TABLE IF NOT EXISTS receipts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id       TEXT    NOT NULL DEFAULT 'local_tenant',
    drive_file_id   TEXT    NOT NULL UNIQUE,
    drive_url       TEXT,
    file_name       TEXT,
    vendor          TEXT,
    amount          REAL,
    receipt_date    DATE,
    ocr_confidence  REAL,
    ocr_mode_used   TEXT,
    raw_ocr_text    TEXT,
    status          TEXT    NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PROCESSED','FAILED','LOW_CONFIDENCE')),
    created_at      DATETIME NOT NULL DEFAULT (datetime('now')),
    updated_at      DATETIME NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_receipt_tenant         ON receipts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_receipt_status         ON receipts (status);
CREATE INDEX IF NOT EXISTS idx_receipt_drive_file_id  ON receipts (drive_file_id);

-- Bank Transactions table
CREATE TABLE IF NOT EXISTS bank_transactions (
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
    created_at       DATETIME NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_bank_tx_tenant   ON bank_transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bank_tx_date     ON bank_transactions (tx_date);
CREATE INDEX IF NOT EXISTS idx_bank_tx_reconciled ON bank_transactions (reconciled);

-- Vendors table (aggregated insights)
CREATE TABLE IF NOT EXISTS vendors (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id         TEXT    NOT NULL DEFAULT 'local_tenant',
    name              TEXT    NOT NULL,
    total_payments    INTEGER NOT NULL DEFAULT 0,
    total_spent       REAL    NOT NULL DEFAULT 0.0,
    last_payment_date DATETIME,
    created_at        DATETIME NOT NULL DEFAULT (datetime('now')),
    UNIQUE (tenant_id, name)
);

-- Audit Trail table (reconciliation mismatches)
CREATE TABLE IF NOT EXISTS audit_trail (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id         TEXT    NOT NULL DEFAULT 'local_tenant',
    transaction_id    INTEGER REFERENCES bank_transactions(id),
    receipt_id        INTEGER REFERENCES receipts(id),
    issue_description TEXT    NOT NULL,
    issue_type        TEXT    CHECK (issue_type IN ('BANK_NO_RECEIPT','RECEIPT_NO_BANK','AMOUNT_MISMATCH','DATE_MISMATCH')),
    resolved          INTEGER NOT NULL DEFAULT 0,
    resolved_at       DATETIME,
    resolved_by       TEXT,
    created_at        DATETIME NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant   ON audit_trail (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_resolved ON audit_trail (resolved);

-- Seed default categories
INSERT OR IGNORE INTO categories (tenant_id, name, type) VALUES
    ('local_tenant', 'Maintenance',       'EXPENSE'),
    ('local_tenant', 'Security',          'EXPENSE'),
    ('local_tenant', 'Utilities',         'EXPENSE'),
    ('local_tenant', 'Housekeeping',      'EXPENSE'),
    ('local_tenant', 'Administrative',    'EXPENSE'),
    ('local_tenant', 'Capital Works',     'EXPENSE'),
    ('local_tenant', 'Insurance',         'EXPENSE'),
    ('local_tenant', 'Legal',             'EXPENSE'),
    ('local_tenant', 'Miscellaneous',     'EXPENSE'),
    ('local_tenant', 'Monthly Dues',      'INCOME'),
    ('local_tenant', 'Penalty Charges',   'INCOME'),
    ('local_tenant', 'Interest Income',   'INCOME');
