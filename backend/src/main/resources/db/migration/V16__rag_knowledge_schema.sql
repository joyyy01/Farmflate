-- RAG knowledge is PostgreSQL system-of-record data. Flyway must run this migration
-- with a deployment role allowed to create the pgvector extension; see docs/ops/pgvector-rollout.md.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS rag;

CREATE TABLE rag.source (
    id uuid PRIMARY KEY,
    canonical_url text NOT NULL UNIQUE,
    publisher text NOT NULL,
    source_name varchar(240) NOT NULL,
    approval_status varchar(32) NOT NULL CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED')),
    approved_by varchar(240),
    approved_at timestamp with time zone,
    expires_at timestamp with time zone,
    content_policy text NOT NULL,
    current_version varchar(120),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((approval_status = 'APPROVED') = (approved_at IS NOT NULL))
);

CREATE TABLE rag.document (
    id uuid PRIMARY KEY,
    source_id uuid NOT NULL REFERENCES rag.source(id),
    ingestion_run_id uuid,
    source_version varchar(120) NOT NULL,
    external_id varchar(500),
    canonical_url text NOT NULL,
    title text,
    language varchar(16) NOT NULL,
    document_status varchar(32) NOT NULL CHECK (document_status IN ('CURRENT', 'SUPERSEDED', 'EXPIRED', 'REJECTED', 'FAILED')),
    content_sha256 char(64) NOT NULL,
    fetched_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone,
    source_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_id, source_version, content_sha256)
);

CREATE TABLE rag.ingestion_run (
    id uuid PRIMARY KEY,
    source_id uuid REFERENCES rag.source(id),
    requested_by varchar(240) NOT NULL,
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'OPERATOR'),
    status varchar(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    document_count integer NOT NULL DEFAULT 0 CHECK (document_count >= 0),
    chunk_count integer NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    failure_reason text,
    manifest_sha256 char(64),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE rag.document
    ADD CONSTRAINT rag_document_ingestion_run_fk
    FOREIGN KEY (ingestion_run_id) REFERENCES rag.ingestion_run(id);

CREATE TABLE rag.chunk (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES rag.document(id),
    ordinal integer NOT NULL CHECK (ordinal >= 0),
    content text NOT NULL,
    content_sha256 char(64) NOT NULL,
    token_count integer NOT NULL CHECK (token_count >= 0),
    search_vector tsvector NOT NULL,
    embedding_model varchar(120) NOT NULL,
    embedding_version varchar(120) NOT NULL,
    embedding_dimensions integer NOT NULL CHECK (embedding_dimensions = 1536),
    embedding vector(1536) NOT NULL,
    chunk_status varchar(32) NOT NULL CHECK (chunk_status IN ('CURRENT', 'SUPERSEDED', 'EXPIRED', 'REJECTED')),
    expires_at timestamp with time zone,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, ordinal, content_sha256)
);

CREATE TABLE rag.eval_case (
    id uuid PRIMARY KEY,
    case_key varchar(160) NOT NULL UNIQUE,
    query_text text NOT NULL,
    language varchar(16) NOT NULL,
    scope jsonb NOT NULL DEFAULT '{}'::jsonb,
    expected_chunk_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    expected_citations jsonb NOT NULL DEFAULT '[]'::jsonb,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rag.eval_result (
    id uuid PRIMARY KEY,
    eval_case_id uuid NOT NULL REFERENCES rag.eval_case(id),
    ingestion_run_id uuid REFERENCES rag.ingestion_run(id),
    evaluated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    embedding_model varchar(120) NOT NULL,
    retrieval_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_status varchar(32) NOT NULL CHECK (result_status IN ('PASSED', 'FAILED', 'ERROR')),
    recall_at_k numeric(6, 5),
    citation_precision numeric(6, 5),
    latency_ms integer CHECK (latency_ms >= 0),
    evidence jsonb NOT NULL DEFAULT '[]'::jsonb,
    failure_reason text
);

CREATE INDEX rag_source_approval_expiry_idx
    ON rag.source (approval_status, expires_at);
CREATE INDEX rag_document_source_status_language_expiry_idx
    ON rag.document (source_id, document_status, language, expires_at);
CREATE INDEX rag_chunk_document_status_expiry_idx
    ON rag.chunk (document_id, chunk_status, expires_at);
CREATE INDEX rag_chunk_search_gin ON rag.chunk USING gin (search_vector);
CREATE INDEX rag_chunk_embedding_hnsw ON rag.chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX rag_ingestion_run_source_status_idx
    ON rag.ingestion_run (source_id, status, created_at DESC);
CREATE INDEX rag_eval_result_case_evaluated_idx
    ON rag.eval_result (eval_case_id, evaluated_at DESC);
