ALTER TABLE rag.agent_execution_trace
    DROP CONSTRAINT IF EXISTS rag_agent_execution_trace_measurement_scope_check;

ALTER TABLE rag.agent_execution_trace
    ADD CONSTRAINT rag_agent_execution_trace_measurement_scope_check
        CHECK (measurement_scope IN ('controlled_local', 'runtime_local', 'legacy_unknown')) NOT VALID;
