from __future__ import annotations

from hashlib import sha256

from app.core.config import settings


def build_execution_profile(
        *,
        pipeline_version: str,
        max_tool_calls: int,
        rag_mode: str,
        top_k: int,
        timeout_seconds: float,
        llm_timeout_seconds: float,
        max_output_tokens: int,
) -> str:
    payload = (
        f"{pipeline_version}|tools={max_tool_calls}|rag={rag_mode}|"
        f"top_k={top_k}|tool_timeout={timeout_seconds:g}|llm_timeout={llm_timeout_seconds:g}|"
        f"max_output_tokens={max_output_tokens}"
    )
    return "agent-" + sha256(payload.encode("utf-8")).hexdigest()[:16]


def configured_execution_profile() -> str:
    return build_execution_profile(
        pipeline_version=settings.AGENT_PIPELINE_VERSION,
        max_tool_calls=settings.AGENT_MAX_TOOL_CALLS,
        rag_mode="hybrid" if settings.RAG_HYBRID_ENABLED else "lexical",
        top_k=settings.RAG_TOP_K,
        timeout_seconds=settings.AGENT_TOOL_TIMEOUT_SECONDS,
        llm_timeout_seconds=settings.LLM_TIMEOUT_SECONDS,
        max_output_tokens=settings.AGENT_MAX_OUTPUT_TOKENS,
    )
