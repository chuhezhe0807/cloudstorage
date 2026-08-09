CREATE EXTENSION IF NOT EXISTS vector;
CREATE DATABASE langfuse;

-- ==============================
-- Tenant
-- ==============================
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'personal',
    quota BIGINT NOT NULL DEFAULT 1073741824,
    used BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==============================
-- User
-- ==============================
CREATE TABLE IF NOT EXISTS "user" (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    account VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'owner',
    locale VARCHAR(10) DEFAULT 'zh',
    theme VARCHAR(10) DEFAULT 'light',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_tenant ON "user"(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_account ON "user"(account);

-- ==============================
-- File meta
-- ==============================
CREATE TABLE IF NOT EXISTS file_meta (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(512) NOT NULL,
    path VARCHAR(2048) NOT NULL,
    is_dir BOOLEAN NOT NULL DEFAULT FALSE,
    size BIGINT NOT NULL DEFAULT 0,
    hash VARCHAR(128),
    content_ref VARCHAR(512),
    mime_type VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fm_tenant ON file_meta(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fm_parent ON file_meta(parent_id);
CREATE INDEX IF NOT EXISTS idx_fm_path ON file_meta(path);
CREATE INDEX IF NOT EXISTS idx_fm_hash ON file_meta(tenant_id, hash);

-- ==============================
-- File content
-- ==============================
CREATE TABLE IF NOT EXISTS file_content (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    hash VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    ref_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_fc_tenant_hash ON file_content(tenant_id, hash);

-- ==============================
-- File chunk
-- ==============================
CREATE TABLE IF NOT EXISTS file_chunk (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    upload_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_hash VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fch_upload ON file_chunk(upload_id);

-- ==============================
-- Share link
-- ==============================
CREATE TABLE IF NOT EXISTS share_link (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256),
    expire_at TIMESTAMP,
    max_downloads INT,
    download_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sl_tenant ON share_link(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sl_code ON share_link(code);

-- ==============================
-- Notification
-- ==============================
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    payload JSONB,
    event_id VARCHAR(128),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notif_tenant_user ON notification(tenant_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_notif_event ON notification(event_id);

-- ==============================
-- Outbox event
-- ==============================
CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT PRIMARY KEY,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    retries INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_oe_status ON outbox_event(status);

-- ==============================
-- RAG reserved (Phase 2)
-- ==============================
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'processing',
    total_files INT NOT NULL DEFAULT 0,
    processed_files INT NOT NULL DEFAULT 0,
    failed_files JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_tenant ON knowledge_base(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_file ON knowledge_base(tenant_id, file_id);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    file_id BIGINT,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kbc_tenant_kb ON kb_chunk(tenant_id, kb_id);
CREATE INDEX IF NOT EXISTS idx_kbc_embedding ON kb_chunk USING hnsw (embedding vector_cosine_ops);
