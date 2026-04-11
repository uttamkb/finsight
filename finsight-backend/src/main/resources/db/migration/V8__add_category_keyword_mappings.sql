CREATE TABLE IF NOT EXISTS category_keyword_mappings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id VARCHAR(255) NOT NULL DEFAULT 'local_tenant',
    keyword VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cat_keyword_tenant ON category_keyword_mappings(tenant_id);
