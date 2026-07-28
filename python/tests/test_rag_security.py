from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from fastapi import HTTPException

from app.core.auth import verify_internal_key
from app.core.config import settings
from app.rag.ingestion import chunk_document
from app.rag.ingest import OperatorIngestor
from app.rag.retriever import PostgresRagRetriever


def test_internal_agent_api_fails_closed_when_key_is_not_configured() -> None:
    original = settings.INTERNAL_API_KEY
    try:
        settings.INTERNAL_API_KEY = ""
        with pytest.raises(HTTPException) as error:
            asyncio.run(verify_internal_key(None))
        assert error.value.status_code == 503
    finally:
        settings.INTERNAL_API_KEY = original


def test_rag_does_not_claim_evidence_when_postgres_retrieval_is_disabled() -> None:
    result = asyncio.run(PostgresRagRetriever(repository=None).retrieve("토양 산도 관리 방법"))
    assert result.insufficient_evidence is True
    assert result.chunks == []


def test_operator_chunking_bounds_content_and_keeps_a_stable_content_hash() -> None:
    chunks = chunk_document("가" * 9 + "\n\n" + "나" * 9, max_chars=10)
    assert [chunk.content for chunk in chunks] == ["가" * 9, "나" * 9]
    assert all(len(chunk.content) <= 10 and len(chunk.content_sha256) == 64 for chunk in chunks)


class _RecordingRepository:
    def __init__(self) -> None:
        self.arguments: dict[str, object] | None = None

    async def ingest_document(self, **_: object):
        self.arguments = _
        return uuid4()


def test_operator_ingestion_stores_fts_chunks_without_embedding_requests() -> None:
    repository = _RecordingRepository()
    ingestor = OperatorIngestor(repository)
    content = "\n\n".join("a" for _ in range(65))

    with pytest.MonkeyPatch.context() as monkeypatch:
        monkeypatch.setattr(settings, "RAG_MAX_CHUNK_CHARS", 1)
        monkeypatch.setattr(settings, "OPENAI_API_KEY", "")
        asyncio.run(ingestor.ingest(
            source_url="https://example.go.kr/guide",
            source_version="v1",
            title="guide",
            language="ko",
            content=content,
            requested_by="operator@example.com",
            fetched_at=datetime.now(UTC),
        ))

    assert repository.arguments is not None
    chunks = repository.arguments["chunks"]
    assert len(chunks) == 65
    assert all(not hasattr(chunk, "embedding") for chunk in chunks)
    assert "embedding_model" not in repository.arguments
