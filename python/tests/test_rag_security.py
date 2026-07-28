from __future__ import annotations

import asyncio

import pytest
from fastapi import HTTPException

from app.core.auth import verify_internal_key
from app.core.config import settings
from app.rag.ingestion import chunk_document
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
