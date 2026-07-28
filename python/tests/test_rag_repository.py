from __future__ import annotations

import asyncio

from app.rag.repository import RagRepository


class RecordingConnection:
    def __init__(self) -> None:
        self.sql = ""
        self.arguments: tuple[object, ...] = ()
        self.rows: list[object] = []

    async def fetch(self, sql: str, *arguments: object) -> list[object]:
        self.sql = sql
        self.arguments = arguments
        return self.rows


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
    assert "c.search_vector @@ websearch_to_tsquery('simple', $1)" in connection.sql
    assert "embedding" not in connection.sql
    assert "::vector" not in connection.sql
    assert connection.arguments == ("토양 관리", "ko", 3)


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
