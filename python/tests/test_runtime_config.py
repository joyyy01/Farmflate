from __future__ import annotations

from unittest.mock import patch

import pytest

from app.core.config import settings


def test_runtime_requires_internal_authentication_and_postgres_rag_configuration() -> None:
    with (
        patch.object(settings, "INTERNAL_API_KEY", ""),
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag:secret@db/rag"),
    ):
        with pytest.raises(RuntimeError, match="INTERNAL_API_KEY"):
            settings.validate_runtime()

    with (
        patch.object(settings, "INTERNAL_API_KEY", "internal-test-key"),
        patch.object(settings, "RAG_DATABASE_URL", ""),
    ):
        with pytest.raises(RuntimeError, match="RAG_DATABASE_URL"):
            settings.validate_runtime()


def test_runtime_rejects_non_postgres_rag_url_and_unbounded_rag_limits() -> None:
    with (
        patch.object(settings, "INTERNAL_API_KEY", "internal-test-key"),
        patch.object(settings, "RAG_DATABASE_URL", "https://example.test/rag"),
    ):
        with pytest.raises(RuntimeError, match="PostgreSQL"):
            settings.validate_runtime()

    with (
        patch.object(settings, "INTERNAL_API_KEY", "internal-test-key"),
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag:secret@db/rag"),
        patch.object(settings, "RAG_TOP_K", 0),
    ):
        with pytest.raises(RuntimeError, match="RAG_TOP_K"):
            settings.validate_runtime()
