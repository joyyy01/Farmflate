from __future__ import annotations

from app.agent.execution_profile import build_execution_profile


def test_execution_profile_changes_when_retrieval_contract_changes() -> None:
    lexical_profile = build_execution_profile(
        pipeline_version="sectioned-citations-v1",
        max_tool_calls=2,
        rag_mode="lexical",
        top_k=8,
        timeout_seconds=10,
        llm_timeout_seconds=45,
        max_output_tokens=800,
    )
    hybrid_profile = build_execution_profile(
        pipeline_version="sectioned-citations-v1",
        max_tool_calls=2,
        rag_mode="hybrid",
        top_k=8,
        timeout_seconds=10,
        llm_timeout_seconds=45,
        max_output_tokens=800,
    )
    slower_timeout_profile = build_execution_profile(
        pipeline_version="sectioned-citations-v1",
        max_tool_calls=2,
        rag_mode="lexical",
        top_k=8,
        timeout_seconds=10,
        llm_timeout_seconds=60,
        max_output_tokens=800,
    )

    assert lexical_profile.startswith("agent-")
    assert lexical_profile != hybrid_profile
    assert lexical_profile != slower_timeout_profile
