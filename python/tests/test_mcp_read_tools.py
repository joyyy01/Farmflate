from __future__ import annotations

import asyncio

from app.mcp.read_tools import McpReadTools
from app.rag.models import RetrievedChunk, RetrievalResult


class _FakeRetriever:
    async def retrieve(self, query: str, **_: object) -> RetrievalResult:
        return RetrievalResult(
            chunks=[RetrievedChunk(
                chunk_id="internal-chunk-id",
                document_id="internal-document-id",
                source_id="internal-source-id",
                source_name="농사로",
                canonical_url="https://example.test/guidance",
                title="고온기 관리",
                content="고온기에는 토양 수분과 작물 상태를 함께 확인하세요.",
                score=0.8,
                metadata={"private": "must-not-leak"},
            )],
            query=query,
            insufficient_evidence=False,
        )


class _UnusedWeatherClient:
    async def read(self, **_: object) -> object:
        raise AssertionError("weather client must not be used by knowledge search")


def test_knowledge_tool_returns_only_citation_safe_fields() -> None:
    tools = McpReadTools(retriever=_FakeRetriever(), weather_client=_UnusedWeatherClient())

    result = asyncio.run(tools.search_approved_agricultural_knowledge(query="고온 장해", limit=3))

    assert result["status"] == "ok"
    assert result["sources"] == [{
        "title": "고온기 관리",
        "url": "https://example.test/guidance",
        "excerpt": "고온기에는 토양 수분과 작물 상태를 함께 확인하세요.",
    }]
