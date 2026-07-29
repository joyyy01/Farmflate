from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from unittest.mock import patch

from app.rag.ingest import OperatorIngestor
from app.rag.embeddings import EmbeddingUnavailable
from app.rag.repository import RagRepository


class _FakePool:
    async def close(self) -> None:
        return None


def test_repository_creates_one_pool_when_first_two_requests_arrive_together() -> None:
    created: list[_FakePool] = []

    async def create_pool(*_: object, **__: object) -> _FakePool:
        await asyncio.sleep(0)
        pool = _FakePool()
        created.append(pool)
        return pool

    repository = RagRepository("postgresql://rag")

    async def open_two_pools() -> tuple[_FakePool, _FakePool]:
        return await asyncio.gather(repository._connection_pool(), repository._connection_pool())

    with patch("app.rag.repository.asyncpg.create_pool", side_effect=create_pool):
        first, second = asyncio.run(open_two_pools())

    assert first is second
    assert len(created) == 1


class _RecordingIngestionRepository:
    def __init__(self) -> None:
        self.chunks: object | None = None
        self.arguments: dict[str, object] = {}

    async def ingest_document(self, **arguments: object) -> str:
        self.arguments = arguments
        self.chunks = arguments["chunks"]
        return "run-1"

    async def semantic_search_available(self) -> bool:
        return True

    async def embedding_egress_allowed(self, **_: object) -> bool:
        return True


class _StaticEmbeddingProvider:
    model = "text-embedding-3-small"
    version = "2026-07"
    dimensions = 1536

    async def embed_documents(self, documents: list[str]) -> list[tuple[float, ...]]:
        return [(0.1,) * self.dimensions for _ in documents]


def test_operator_ingestion_persists_the_embedding_contract_with_each_chunk() -> None:
    from datetime import UTC, datetime

    repository = _RecordingIngestionRepository()
    ingestor = OperatorIngestor(repository, embedding_provider=_StaticEmbeddingProvider())  # type: ignore[arg-type]

    run_id = asyncio.run(ingestor.ingest(
        source_url="https://example.test/soil",
        source_version="2026-07-29",
        title="토양 관리",
        language="ko",
        content="배수가 좋은 토양을 유지하세요.",
        requested_by="operator@example.test",
        fetched_at=datetime.now(UTC),
    ))

    assert run_id == "run-1"
    assert repository.chunks is not None
    first = repository.chunks[0]  # type: ignore[index]
    assert first.embedding == (0.1,) * 1536
    assert first.embedding_model == "text-embedding-3-small"
    assert first.embedding_dimensions == 1536


def test_operator_ingestion_forwards_a_publication_identifier_for_targeted_refresh() -> None:
    repository = _RecordingIngestionRepository()
    ingestor = OperatorIngestor(repository, embedding_provider=None)  # type: ignore[arg-type]

    asyncio.run(ingestor.ingest(
        source_url="https://example.test/weekly",
        source_version="weekly:42:2026-07-29",
        external_id="weekly:42",
        title="주간 농사정보",
        language="ko",
        content="배수로와 병해충 발생 여부를 함께 확인하세요.",
        requested_by="operator@example.test",
        fetched_at=datetime.now(UTC),
    ))

    assert repository.arguments["external_id"] == "weekly:42"


class _UnavailableEmbeddingProvider:
    model = "text-embedding-3-small"
    version = "openai-v1"
    dimensions = 1536

    async def embed_documents(self, _: list[str]) -> list[tuple[float, ...]]:
        raise EmbeddingUnavailable("upstream timeout")


def test_operator_ingestion_records_a_lexical_only_run_when_embedding_is_unavailable() -> None:
    from datetime import UTC, datetime

    repository = _RecordingIngestionRepository()
    ingestor = OperatorIngestor(repository, embedding_provider=_UnavailableEmbeddingProvider())  # type: ignore[arg-type]

    asyncio.run(ingestor.ingest(
        source_url="https://example.test/soil",
        source_version="2026-07-29",
        title="토양 관리",
        language="ko",
        content="배수가 좋은 토양을 유지하세요.",
        requested_by="operator@example.test",
        fetched_at=datetime.now(UTC),
    ))

    assert repository.chunks is not None
    assert repository.chunks[0].embedding is None  # type: ignore[index]
    assert repository.arguments["embedding_status"] == "UNAVAILABLE"  # type: ignore[attr-defined]
    assert repository.arguments["embedding_failure_reason"] == "upstream timeout"  # type: ignore[attr-defined]


class _VectorUnavailableRepository(_RecordingIngestionRepository):
    async def semantic_search_available(self) -> bool:
        return False


class _UnexpectedEmbeddingProvider:
    model = "text-embedding-3-small"
    version = "openai-v1"
    dimensions = 1536

    async def embed_documents(self, _: list[str]) -> list[tuple[float, ...]]:
        raise AssertionError("pgvector is unavailable, so this provider must not be called")


def test_operator_ingestion_skips_embedding_cost_when_postgres_semantic_search_is_unavailable() -> None:
    from datetime import UTC, datetime

    repository = _VectorUnavailableRepository()
    ingestor = OperatorIngestor(repository, embedding_provider=_UnexpectedEmbeddingProvider())  # type: ignore[arg-type]

    asyncio.run(ingestor.ingest(
        source_url="https://example.test/soil",
        source_version="2026-07-29",
        title="토양 관리",
        language="ko",
        content="배수가 좋은 토양을 유지하세요.",
        requested_by="operator@example.test",
        fetched_at=datetime.now(UTC),
    ))

    assert repository.arguments["embedding_status"] == "UNAVAILABLE"
    assert repository.arguments["embedding_failure_reason"] == "pgvector is not available."


class _EgressDeniedRepository(_RecordingIngestionRepository):
    async def embedding_egress_allowed(self, **_: object) -> bool:
        return False


def test_operator_ingestion_never_calls_an_external_embedding_provider_without_source_egress_approval() -> None:
    from datetime import UTC, datetime

    repository = _EgressDeniedRepository()
    ingestor = OperatorIngestor(repository, embedding_provider=_UnexpectedEmbeddingProvider())  # type: ignore[arg-type]

    asyncio.run(ingestor.ingest(
        source_url="https://example.test/restricted",
        source_version="2026-07-29",
        title="내부 자료",
        language="ko",
        content="이 문서는 PostgreSQL 전문 검색으로만 처리합니다.",
        requested_by="operator@example.test",
        fetched_at=datetime.now(UTC),
    ))

    assert repository.arguments["embedding_status"] == "POLICY_DENIED"
    assert repository.arguments["embedding_failure_reason"] == "Source policy does not permit external embedding."
