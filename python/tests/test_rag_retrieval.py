from __future__ import annotations

import asyncio
from unittest.mock import patch

from app.core.config import settings
from app.rag.models import RetrievedChunk
from app.rag.retriever import HybridRetriever


class RecordingRepository:
    def __init__(self) -> None:
        self.arguments: dict[str, object] | None = None

    async def hybrid_search(self, **arguments: object) -> list[RetrievedChunk]:
        self.arguments = arguments
        return [RetrievedChunk(
            chunk_id="chunk-1", document_id="document-1", source_id="source-1",
            source_name="농사로", canonical_url="https://example.go.kr/soil",
            title="토양 관리", content="토양 상태를 확인하세요.", score=0.04, metadata={},
        )]


class FixedEmbeddingClient:
    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[0.01] * 1536 for _ in texts]


def test_hybrid_retrieval_preserves_the_search_result_as_a_citable_chunk() -> None:
    repository = RecordingRepository()
    with (
        patch.object(settings, "RAG_ENABLED", True),
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag"),
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
    ):
        result = asyncio.run(HybridRetriever(repository=repository, embeddings=FixedEmbeddingClient()).retrieve("토양 관리"))

    assert result.insufficient_evidence is False
    assert result.chunks[0].citation()["citationId"] == "rag:chunk-1"
    assert repository.arguments is not None
    assert repository.arguments["query_text"] == "토양 관리"
