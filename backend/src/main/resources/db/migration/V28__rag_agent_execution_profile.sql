ALTER TABLE rag.agent_execution_trace
    ADD COLUMN IF NOT EXISTS pipeline_version VARCHAR(64) NOT NULL DEFAULT 'legacy-unversioned',
    ADD COLUMN IF NOT EXISTS model_name VARCHAR(128) NOT NULL DEFAULT 'legacy-unversioned';

CREATE INDEX IF NOT EXISTS idx_agent_execution_trace_profile_created
    ON rag.agent_execution_trace (pipeline_version, model_name, created_at DESC);

COMMENT ON COLUMN rag.agent_execution_trace.pipeline_version IS
    'Version of the Agent prompt, schema, tool policy, and validation contract; excludes prompt and answer text.';
COMMENT ON COLUMN rag.agent_execution_trace.model_name IS
    'Configured model identifier only; excludes provider responses, keys, and token content.';
