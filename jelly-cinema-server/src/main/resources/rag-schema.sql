CREATE TABLE IF NOT EXISTS rag_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    domain VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    embedding_model VARCHAR(128),
    chunk_size INT NOT NULL DEFAULT 260,
    chunk_overlap INT NOT NULL DEFAULT 40,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES rag_knowledge_base(id) ON DELETE CASCADE,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_uri VARCHAR(255),
    content_hash VARCHAR(128) NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_document_kb_biz
    ON rag_document(knowledge_base_id, biz_type, biz_id);

CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    knowledge_base_code VARCHAR(64) NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    chunk_no INT NOT NULL,
    chunk_text TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    metadata_json TEXT,
    milvus_collection VARCHAR(128),
    milvus_primary_key BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_chunk_kb_biz
    ON rag_chunk(knowledge_base_code, biz_type, biz_id);

CREATE TABLE IF NOT EXISTS rag_ingest_task (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT REFERENCES rag_knowledge_base(id) ON DELETE SET NULL,
    task_type VARCHAR(32) NOT NULL,
    scope_key VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    source_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_ingest_task_start_time
    ON rag_ingest_task(start_time DESC);