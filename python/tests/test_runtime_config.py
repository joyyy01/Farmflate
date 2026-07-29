from __future__ import annotations

import os
from unittest.mock import patch

import pytest

from app.core.config import Settings, settings
from app.rag.embeddings import configured_embedding_provider


def test_shared_env_names_build_python_rag_configuration() -> None:
    with patch.dict(
        os.environ,
        {
            "PYTHON_INTERNAL_API_KEY": "internal-test-key",
            "DB_HOST": "127.0.0.1",
            "DB_PORT": "5432",
            "DB_NAME": "farmflate",
            "DB_USER": "farmflate",
        },
        clear=True,
    ):
        configured = Settings(_env_file=None)

    assert configured.INTERNAL_API_KEY == "internal-test-key"
    assert configured.RAG_DATABASE_URL == "postgresql://farmflate@127.0.0.1:5432/farmflate"


def test_mcp_is_disabled_without_explicit_environment_values() -> None:
    configured = Settings(_env_file=None)

    assert configured.MCP_ENABLED is False
    assert configured.LLM_TIMEOUT_SECONDS == 45
    assert configured.AGENT_MAX_OUTPUT_TOKENS == 800


def test_rag_evaluation_default_requires_at_least_thirty_cases() -> None:
    configured = Settings(_env_file=None)

    assert configured.RAG_EVALUATION_MIN_CASES == 30


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


def test_semantic_retrieval_requires_an_explicit_opt_in_even_when_an_openai_key_exists() -> None:
    with (
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
        patch.object(settings, "RAG_HYBRID_ENABLED", False),
    ):
        assert configured_embedding_provider() is None

    with (
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
        patch.object(settings, "RAG_HYBRID_ENABLED", True),
    ):
        assert configured_embedding_provider() is not None


def test_runtime_rejects_an_unbounded_llm_response_timeout() -> None:
    with (
        patch.object(settings, "INTERNAL_API_KEY", "internal-test-key"),
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag:secret@db/rag"),
        patch.object(settings, "LLM_TIMEOUT_SECONDS", 61),
    ):
        with pytest.raises(RuntimeError, match="LLM_TIMEOUT_SECONDS"):
            settings.validate_runtime()
