CREATE TABLE IF NOT EXISTS vendor_aliases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id VARCHAR(255) NOT NULL DEFAULT 'local_tenant',
    canonical_name VARCHAR(255) NOT NULL,
    alias_name VARCHAR(255) NOT NULL,
    category_id BIGINT,
    approval_count INTEGER NOT NULL DEFAULT 0,
    confidence_score DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_vendor_alias_tenant ON vendor_aliases(tenant_id);
CREATE INDEX IF NOT EXISTS idx_vendor_alias_name ON vendor_aliases(alias_name);
