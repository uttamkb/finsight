-- Flyway Migration V9: Add missing tables for workflow tracking
-- This script creates tables for statement uploads, reconciliation runs, and receipt sync runs.

-- statement_uploads table (for bank statement processing metadata)
CREATE TABLE IF NOT EXISTS statement_uploads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id TEXT NOT NULL UNIQUE,
    tenant_id TEXT NOT NULL DEFAULT 'local_tenant',
    file_path TEXT NOT NULL,
    file_name TEXT,
    file_hash TEXT,
    status TEXT,
    account_type TEXT,
    processing_time_ms INTEGER,
    gemini_calls_count INTEGER,
    avg_confidence_score REAL,
    source TEXT,
    last_processed_at DATETIME,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT (datetime('now')),
    updated_at DATETIME NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_upload_tenant ON statement_uploads (tenant_id);
CREATE INDEX IF NOT EXISTS idx_upload_hash ON statement_uploads (file_hash);
CREATE INDEX IF NOT EXISTS idx_upload_file_id ON statement_uploads (file_id);

-- reconciliation_runs table (for reconciliation engine history)
CREATE TABLE IF NOT EXISTS reconciliation_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id TEXT NOT NULL DEFAULT 'local_tenant',
    account_type TEXT NOT NULL,
    started_at DATETIME NOT NULL DEFAULT (datetime('now')),
    completed_at DATETIME,
    matched_count INTEGER DEFAULT 0,
    manual_review_count INTEGER DEFAULT 0,
    status TEXT,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_recon_run_tenant ON reconciliation_runs (tenant_id);

-- receipt_sync_runs table (for receipt synchronization tracking)
CREATE TABLE IF NOT EXISTS receipt_sync_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id TEXT NOT NULL DEFAULT 'local_tenant',
    started_at DATETIME NOT NULL DEFAULT (datetime('now')),
    completed_at DATETIME,
    status TEXT,
    stage TEXT,
    total_files INTEGER DEFAULT 0,
    processed_files INTEGER DEFAULT 0,
    failed_files INTEGER DEFAULT 0,
    skipped_files INTEGER DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_sync_run_tenant ON receipt_sync_runs (tenant_id);