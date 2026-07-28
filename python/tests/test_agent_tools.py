from __future__ import annotations

import asyncio
from unittest.mock import patch

from app.agent.tools import AgentToolExecutor
from app.core.config import settings
from app.schemas.chat import FactPackage


class _SlowRetriever:
    async def retrieve(self, _query: str) -> object:
        await asyncio.sleep(0.02)
        raise AssertionError("The tool timeout should cancel this retrieval.")


class _UnavailableRetriever:
    async def retrieve(self, _query: str) -> object:
        raise RuntimeError("database is unavailable")


def test_agent_tool_executor_rejects_any_tool_outside_the_read_only_allow_list() -> None:
    result = asyncio.run(
        AgentToolExecutor().execute(
            name="update_field",
            arguments={"fieldId": "another-users-field", "status": "DONE"},
            fact_package=FactPackage(requestId="request-1", question="밭 상태를 바꿔줘"),
        )
    )

    assert result.status == "tool_not_allowed"
    assert result.citations == []


def test_authorized_context_cites_only_sources_proven_for_the_selected_facts() -> None:
    result = asyncio.run(
        AgentToolExecutor().execute(
            name="read_authorized_context",
            arguments={"section": "field"},
            fact_package=FactPackage(
                requestId="request-2",
                question="내 밭 상태를 알려줘",
                facts={"field.status": "CAUTION", "risk.title": "고온"},
                sources=[
                    {"sourceId": "field-report", "factKeyPrefixes": ["field."], "provider": "Farmflate"},
                    {"sourceId": "risk-report", "factKeyPrefixes": ["risk."], "provider": "Farmflate"},
                    {"sourceId": "legacy-report", "provider": "Farmflate"},
                ],
            ),
        )
    )

    assert result.payload["facts"] == {"field.status": "CAUTION"}
    assert [citation.citation_id for citation in result.citations] == ["fact:field-report"]


def test_knowledge_search_returns_tool_timeout_when_retrieval_exceeds_its_boundary() -> None:
    with patch.object(settings, "AGENT_TOOL_TIMEOUT_SECONDS", 0.001):
        result = asyncio.run(
            AgentToolExecutor(_SlowRetriever()).execute(
                name="search_approved_knowledge",
                arguments={"query": "고온 피해 대응"},
                fact_package=FactPackage(requestId="request-3", question="고온 피해 대응"),
            )
        )

    assert result.status == "tool_timeout"
    assert result.citations == []


def test_knowledge_search_returns_tool_unavailable_when_retrieval_fails() -> None:
    result = asyncio.run(
        AgentToolExecutor(_UnavailableRetriever()).execute(
            name="search_approved_knowledge",
            arguments={"query": "고온 피해 대응"},
            fact_package=FactPackage(requestId="request-4", question="고온 피해 대응"),
        )
    )

    assert result.status == "tool_unavailable"
    assert result.citations == []
