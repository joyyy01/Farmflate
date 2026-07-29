-- Preserve who approved, rejected, or revoked a RAG source and why.
-- Retrieval continues to read only rag.source.approval_status = 'APPROVED'.
CREATE TABLE rag.source_audit_event (
    id uuid PRIMARY KEY,
    source_id uuid NOT NULL REFERENCES rag.source(id),
    action varchar(32) NOT NULL CHECK (action IN ('REGISTERED', 'APPROVED', 'REJECTED', 'REVOKED')),
    actor varchar(240) NOT NULL,
    reason varchar(1000) NOT NULL,
    occurred_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX rag_source_audit_event_source_occurred_idx
    ON rag.source_audit_event (source_id, occurred_at DESC);
