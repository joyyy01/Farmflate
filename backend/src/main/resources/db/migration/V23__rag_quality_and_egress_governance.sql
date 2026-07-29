-- A source may be readable in PostgreSQL without being eligible for external
-- embedding. Default-deny prevents a hybrid rollout from silently exporting
-- previously approved content to an embedding provider.
ALTER TABLE rag.source
    ADD COLUMN IF NOT EXISTS embedding_egress_allowed boolean NOT NULL DEFAULT false;

ALTER TABLE rag.source_audit_event
    DROP CONSTRAINT IF EXISTS source_audit_event_action_check;
ALTER TABLE rag.source_audit_event
    ADD CONSTRAINT source_audit_event_action_check
    CHECK (action IN (
        'REGISTERED', 'APPROVED', 'REJECTED', 'REVOKED',
        'EMBEDDING_EGRESS_ALLOWED', 'EMBEDDING_EGRESS_REVOKED'
    ));

ALTER TABLE rag.ingestion_run
    DROP CONSTRAINT IF EXISTS rag_ingestion_embedding_status_ck;
ALTER TABLE rag.ingestion_run
    ADD CONSTRAINT rag_ingestion_embedding_status_ck
    CHECK (embedding_status IN ('NOT_REQUESTED', 'READY', 'UNAVAILABLE', 'FAILED', 'POLICY_DENIED'));

-- A run fixes the exact evaluation corpus and retrieval configuration so that
-- two modes are never compared across different documents or question sets.
ALTER TABLE rag.eval_case
    ADD COLUMN IF NOT EXISTS dataset_key varchar(120) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS dataset_version varchar(120) NOT NULL DEFAULT 'v1';
CREATE INDEX rag_eval_case_dataset_status_idx
    ON rag.eval_case (dataset_key, dataset_version, status, case_key);

CREATE TABLE rag.eval_run (
    id uuid PRIMARY KEY,
    dataset_key varchar(120) NOT NULL,
    dataset_version varchar(120) NOT NULL,
    retrieval_mode varchar(32) NOT NULL CHECK (retrieval_mode IN ('lexical', 'hybrid')),
    retrieval_config jsonb NOT NULL,
    requested_by varchar(240) NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    case_count integer NOT NULL DEFAULT 0 CHECK (case_count >= 0),
    started_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp with time zone,
    failure_reason text
);

CREATE INDEX rag_eval_run_dataset_completed_idx
    ON rag.eval_run (dataset_key, dataset_version, completed_at DESC);

ALTER TABLE rag.eval_result
    ADD COLUMN IF NOT EXISTS eval_run_id uuid REFERENCES rag.eval_run(id);
CREATE INDEX rag_eval_result_run_case_idx
    ON rag.eval_result (eval_run_id, eval_case_id);
