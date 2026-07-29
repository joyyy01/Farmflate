-- Store only aggregate Agent behavior. Prompts, answers, facts, retrieved text,
-- provider response bodies, and credentials must never enter this table.
CREATE TABLE IF NOT EXISTS rag.agent_execution_trace (
    id uuid PRIMARY KEY,
    request_id varchar(80) NOT NULL,
    terminal_status varchar(32) NOT NULL
        CHECK (terminal_status IN ('completed', 'needs_context', 'failed')),
    terminal_reason varchar(64) NOT NULL,
    model_turn_count smallint NOT NULL CHECK (model_turn_count >= 0),
    tool_call_count smallint NOT NULL CHECK (tool_call_count >= 0),
    tool_non_success_count smallint NOT NULL CHECK (tool_non_success_count >= 0),
    citation_count smallint NOT NULL CHECK (citation_count >= 0),
    answer_char_count integer NOT NULL CHECK (answer_char_count >= 0),
    total_latency_ms integer NOT NULL CHECK (total_latency_ms >= 0),
    model_latency_ms integer NOT NULL CHECK (model_latency_ms >= 0),
    tool_latency_ms integer NOT NULL CHECK (tool_latency_ms >= 0),
    tool_statuses jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS rag_agent_execution_trace_created_at_idx
    ON rag.agent_execution_trace (created_at DESC);

CREATE INDEX IF NOT EXISTS rag_agent_execution_trace_status_created_at_idx
    ON rag.agent_execution_trace (terminal_status, created_at DESC);
