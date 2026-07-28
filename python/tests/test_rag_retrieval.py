from __future__ import annotations

import asyncio
from unittest.mock import patch

from app.agent.tools import AgentToolExecutor
from app.core.config import settings
from app.rag.models import RetrievedChunk
from app.rag.retriever import HybridRetriever
from app.schemas.chat import FactPackage


class RecordingRepository:
    def __init__(self) -> None:
        self.arguments: dict[str, object] | None = None

    async def search(self, **arguments: object) -> list[RetrievedChunk]:
        self.arguments = arguments
        return [RetrievedChunk(
            chunk_id="chunk-1", document_id="document-1", source_id="source-1",
            source_name="농사로", canonical_url="https://example.go.kr/soil",
            title="토양 관리", content="토양 상태를 확인하세요.", score=0.04, metadata={},
        )]


def test_postgres_fts_retrieval_preserves_the_search_result_without_an_openai_key() -> None:
    repository = RecordingRepository()
    with (
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag"),
        patch.object(settings, "OPENAI_API_KEY", ""),
    ):
        result = asyncio.run(HybridRetriever(repository=repository).retrieve("토양 관리"))

    assert result.insufficient_evidence is False
    assert result.chunks[0].citation()["citationId"] == "rag:chunk-1"
    assert repository.arguments is not None
    assert repository.arguments["query_text"] == "토양 관리"
    assert repository.arguments["top_k"] == settings.RAG_TOP_K


def test_knowledge_tool_uses_postgres_fts_without_an_openai_key() -> None:
    repository = RecordingRepository()
    with (
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag"),
        patch.object(settings, "OPENAI_API_KEY", ""),
    ):
        result = asyncio.run(
            AgentToolExecutor(HybridRetriever(repository=repository)).execute(
                name="search_approved_knowledge",
                arguments={"query": "토양 관리"},
                fact_package=FactPackage(requestId="rag-tool", question="토양 관리"),
            )
        )

    assert result.status == "ok"
    assert [citation.citation_id for citation in result.citations] == ["rag:chunk-1"]
