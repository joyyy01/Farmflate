from __future__ import annotations

import asyncio
from datetime import UTC, datetime

from app.rag.repository import ChunkInsert, RagRepository


class RecordingConnection:
    def __init__(self) -> None:
        self.sql = ""
        self.arguments: tuple[object, ...] = ()
        self.rows: list[object] = []

    async def fetch(self, sql: str, *arguments: object) -> list[object]:
        self.sql = sql
        self.arguments = arguments
        return self.rows

    async def fetchval(self, sql: str, *_: object) -> bool:
        self.capability_sql = sql
        self.fetchval_calls = getattr(self, "fetchval_calls", 0) + 1
        return True


class AcquiredConnection:
    def __init__(self, connection: RecordingConnection) -> None:
        self._connection = connection

    async def __aenter__(self) -> RecordingConnection:
        return self._connection

    async def __aexit__(self, *_: object) -> None:
        return None


class RecordingPool:
    def __init__(self, connection: RecordingConnection) -> None:
        self._connection = connection

    def acquire(self) -> AcquiredConnection:
        return AcquiredConnection(self._connection)


def test_full_text_search_uses_only_postgresql_fts_on_current_approved_chunks() -> None:
    connection = RecordingConnection()
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    rows = asyncio.run(repository.search(
        query_text="토양 관리",
        top_k=3,
        language="ko",
    ))

    assert rows == []
    assert connection.sql.count("FROM rag.chunk c") == 1
    assert "c.chunk_status = 'CURRENT'" in connection.sql
    assert "regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g')" in connection.sql
    assert "embedding" not in connection.sql
    assert "::vector" not in connection.sql
    assert connection.arguments == ("토양 관리", "ko", 3)


def test_semantic_capability_cache_is_refreshed_after_activation_window() -> None:
    connection = RecordingConnection()
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]
    repository._semantic_available = False
    repository._semantic_checked_at = 0.0

    assert asyncio.run(repository.semantic_search_available()) is True
    assert connection.fetchval_calls == 1


def test_full_text_search_normalizes_json_string_metadata() -> None:
    connection = RecordingConnection()
    connection.rows = [{
        "chunk_id": "chunk-1",
        "document_id": "document-1",
        "source_id": "source-1",
        "source_name": "농업 기술 자료",
        "canonical_url": "https://example.test/source",
        "title": "토양 관리",
        "content": "배수가 좋은 토양을 유지하세요.",
        "score": 0.9,
        "metadata": '{"category":"soil"}',
    }]
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    rows = asyncio.run(repository.search(query_text="토양", top_k=1))

    assert rows[0].metadata == {"category": "soil"}


def test_hybrid_search_fuses_postgresql_fts_and_pgvector_candidates_with_rrf() -> None:
    connection = RecordingConnection()
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    result = asyncio.run(repository.search_hybrid(
        query_text="토양 관리",
        query_embedding=(0.1,) * 1536,
        embedding_model="text-embedding-3-small",
        embedding_dimensions=1536,
        top_k=3,
        language="ko",
    ))

    assert result.mode == "hybrid"
    assert result.chunks == []
    assert "regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g')" in connection.sql
    assert "<=> $2::vector" in connection.sql
    assert "1.0 / ($7 + lexical_rank)" in connection.sql
    assert "1.0 / ($7 + semantic_rank)" in connection.sql
    assert connection.arguments[0] == "토양 관리"
    assert connection.arguments[1].startswith("[0.1,0.1,0.1")


class IngestionConnection:
    def __init__(self) -> None:
        self.executemany_sql = ""
        self.executemany_arguments: list[tuple[object, ...]] = []
        self.executions: list[tuple[str, tuple[object, ...]]] = []

    async def fetchval(self, sql: str, *_: object) -> object:
        if "FROM rag.source" in sql:
            return "source-1"
        if "INSERT INTO rag.document" in sql:
            return "document-1"
        return True

    async def execute(self, sql: str, *arguments: object) -> None:
        self.executions.append((sql, arguments))
        return None

    async def executemany(self, sql: str, arguments: list[tuple[object, ...]]) -> None:
        self.executemany_sql = sql
        self.executemany_arguments = arguments

    def transaction(self) -> "IngestionTransaction":
        return IngestionTransaction()


class IngestionTransaction:
    async def __aenter__(self) -> None:
        return None

    async def __aexit__(self, *_: object) -> None:
        return None


def test_ingestion_writes_vector_metadata_only_when_the_pgvector_contract_is_available() -> None:
    connection = IngestionConnection()
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    asyncio.run(repository.ingest_document(
        source_url="https://example.test/soil",
        source_version="2026-07-29",
        title="토양 관리",
        language="ko",
        content_sha256="a" * 64,
        fetched_at=datetime.now(UTC),
        requested_by="operator@example.test",
        embedding_status="READY",
        embedding_model="text-embedding-3-small",
        embedding_dimensions=1536,
        chunks=[ChunkInsert(
            ordinal=0,
            content="배수가 좋은 토양을 유지하세요.",
            content_sha256="b" * 64,
            embedding=(0.1,) * 1536,
            embedding_model="text-embedding-3-small",
            embedding_version="openai-v1",
            embedding_dimensions=1536,
        )],
    ))

    assert "embedding_model" in connection.executemany_sql
    assert "::vector" in connection.executemany_sql
    assert "ON CONFLICT (document_id, ordinal, content_sha256) DO UPDATE" in connection.executemany_sql
    assert connection.executemany_arguments[0][-1] == "[" + ",".join(["0.1"] * 1536) + "]"


class VectorUnavailableConnection(IngestionConnection):
    async def fetchval(self, sql: str, *_: object) -> object:
        if "FROM rag.source" in sql:
            return "source-1"
        return False


class EgressDeniedConnection(IngestionConnection):
    async def fetchval(self, sql: str, *_: object) -> object:
        if "embedding_egress_allowed" in sql:
            return False
        return await super().fetchval(sql)


def test_ingestion_marks_a_ready_embedding_as_unavailable_when_pgvector_is_not_installed() -> None:
    connection = VectorUnavailableConnection()
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    asyncio.run(repository.ingest_document(
        source_url="https://example.test/soil",
        source_version="2026-07-29",
        title="토양 관리",
        language="ko",
        content_sha256="a" * 64,
        fetched_at=datetime.now(UTC),
        requested_by="operator@example.test",
        embedding_status="READY",
        embedding_model="text-embedding-3-small",
        embedding_dimensions=1536,
        chunks=[ChunkInsert(
            ordinal=0,
            content="배수가 좋은 토양을 유지하세요.",
            content_sha256="b" * 64,
            embedding=(0.1,) * 1536,
            embedding_model="text-embedding-3-small",
            embedding_version="openai-v1",
            embedding_dimensions=1536,
        )],
    ))

    ingestion_insert = next(arguments for sql, arguments in connection.executions if "INSERT INTO rag.ingestion_run" in sql)
    assert ingestion_insert[3] == "UNAVAILABLE"
    assert "embedding_model" not in connection.executemany_sql


def test_repository_defense_in_depth_blocks_vector_write_when_source_disallows_external_embedding() -> None:
    connection = EgressDeniedConnection()
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    asyncio.run(repository.ingest_document(
        source_url="https://example.test/restricted",
        source_version="2026-07-29",
        title="제한 자료",
        language="ko",
        content_sha256="a" * 64,
        fetched_at=datetime.now(UTC),
        requested_by="operator@example.test",
        embedding_status="READY",
        embedding_model="text-embedding-3-small",
        embedding_dimensions=1536,
        chunks=[ChunkInsert(
            ordinal=0,
            content="외부 임베딩으로 보내면 안 되는 내용입니다.",
            content_sha256="b" * 64,
            embedding=(0.1,) * 1536,
            embedding_model="text-embedding-3-small",
            embedding_version="openai-v1",
            embedding_dimensions=1536,
        )],
    ))

    ingestion_insert = next(arguments for sql, arguments in connection.executions if "INSERT INTO rag.ingestion_run" in sql)
    assert ingestion_insert[3] == "POLICY_DENIED"
    assert "embedding_model" not in connection.executemany_sql


def test_quality_metrics_keep_lexical_and_hybrid_results_separate() -> None:
    connection = RecordingConnection()
    connection.rows = [{
        "retrieval_mode": "hybrid",
        "evaluation_count": 12,
        "avg_recall_at_k": 0.83,
        "avg_citation_precision": 0.92,
        "request_count": 85,
        "p50_latency_ms": 41.0,
        "p95_latency_ms": 97.0,
    }]
    repository = RagRepository("postgresql://rag")
    repository._pool = RecordingPool(connection)  # type: ignore[assignment]

    metrics = asyncio.run(repository.retrieval_quality_metrics(days=30))

    assert metrics[0].mode == "hybrid"
    assert metrics[0].evaluation_count == 12
    assert metrics[0].p95_latency_ms == 97.0
    assert "percentile_cont(0.95)" in connection.sql
    assert connection.arguments == (30,)
