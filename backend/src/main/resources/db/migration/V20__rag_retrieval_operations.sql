-- Why this migration exists:
-- V16/V17 preserve approved sources and make a semantic lane possible, but they
-- cannot distinguish a lexical-only ingestion from an embedding failure or
-- measure real retrieval quality. These records keep that operational evidence
-- in PostgreSQL without storing a user's original question or secret values.

ALTER TABLE rag.ingestion_run
    ADD COLUMN IF NOT EXISTS embedding_status varchar(32) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN IF NOT EXISTS embedding_model varchar(120),
    ADD COLUMN IF NOT EXISTS embedding_dimensions integer,
    ADD COLUMN IF NOT EXISTS embedding_failure_reason text;

ALTER TABLE rag.ingestion_run
    ADD CONSTRAINT rag_ingestion_embedding_status_ck
    CHECK (embedding_status IN ('NOT_REQUESTED', 'READY', 'UNAVAILABLE', 'FAILED'));

ALTER TABLE rag.eval_result
    ADD COLUMN IF NOT EXISTS retrieval_mode varchar(32) NOT NULL DEFAULT 'lexical';

CREATE TABLE IF NOT EXISTS rag.retrieval_trace (
    id uuid PRIMARY KEY,
    request_id varchar(80) NOT NULL,
    query_sha256 char(64) NOT NULL,
    retrieval_mode varchar(32) NOT NULL CHECK (retrieval_mode IN ('lexical', 'hybrid')),
    retrieval_status varchar(32) NOT NULL CHECK (retrieval_status IN ('COMPLETED', 'INSUFFICIENT_EVIDENCE', 'FAILED')),
    candidate_count integer NOT NULL CHECK (candidate_count >= 0),
    returned_chunk_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    latency_ms integer NOT NULL CHECK (latency_ms >= 0),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS rag_retrieval_trace_created_idx
    ON rag.retrieval_trace (created_at DESC);
