ALTER TABLE rag.agent_execution_trace
    ADD COLUMN IF NOT EXISTS execution_profile VARCHAR(64) NOT NULL DEFAULT 'legacy-unknown',
    ADD COLUMN IF NOT EXISTS measurement_scope VARCHAR(32) NOT NULL DEFAULT 'legacy_unknown';

ALTER TABLE rag.agent_execution_trace
    ADD CONSTRAINT rag_agent_execution_trace_measurement_scope_check
    CHECK (measurement_scope IN ('controlled_local', 'legacy_unknown')) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_agent_execution_trace_execution_profile_created
    ON rag.agent_execution_trace (execution_profile, model_name, created_at DESC);

COMMENT ON COLUMN rag.agent_execution_trace.execution_profile IS
    'Hash of non-secret Agent execution contract settings; excludes prompt, response, fact package, and keys.';
COMMENT ON COLUMN rag.agent_execution_trace.measurement_scope IS
    'Evidence scope. controlled_local is not equivalent to commercial production verification.';
