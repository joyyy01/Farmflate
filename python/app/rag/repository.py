from __future__ import annotations

import asyncio
import json
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID, uuid4

import asyncpg

from app.rag.models import RetrievedChunk


def _metadata_as_dict(value: object) -> dict[str, object]:
    """Normalize asyncpg JSONB values across codec configurations."""
    if isinstance(value, dict):
        return dict(value)
    if isinstance(value, str):
        try:
            decoded = json.loads(value)
        except json.JSONDecodeError:
            return {}
        return dict(decoded) if isinstance(decoded, dict) else {}
    return {}


@dataclass(frozen=True)
class ChunkInsert:
    ordinal: int
    content: str
    content_sha256: str


class RagRepository:
    """PostgreSQL full-text retrieval over approved, current source versions."""

    def __init__(self, database_url: str) -> None:
        self._database_url = database_url
        self._pool: asyncpg.Pool | None = None
        self._pool_lock = asyncio.Lock()

    async def _connection_pool(self) -> asyncpg.Pool:
        if self._pool is None:
            async with self._pool_lock:
                if self._pool is None:
                    self._pool = await asyncpg.create_pool(
                        self._database_url, min_size=1, max_size=4, command_timeout=8
                    )
        return self._pool

    async def close(self) -> None:
        async with self._pool_lock:
            pool, self._pool = self._pool, None
        if pool is not None:
            await pool.close()

    async def search(
        self,
        *,
        query_text: str,
        top_k: int,
        language: str | None = "ko",
    ) -> list[RetrievedChunk]:
        sql = """
            SELECT c.id::text AS chunk_id, c.document_id::text, d.source_id::text, s.source_name,
                   d.canonical_url, d.title, c.content,
                   ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', $1)) AS score, c.metadata
            FROM rag.chunk c
            JOIN rag.document d ON d.id = c.document_id
            JOIN rag.source s ON s.id = d.source_id
            WHERE c.chunk_status = 'CURRENT'
              AND (c.expires_at IS NULL OR c.expires_at > CURRENT_TIMESTAMP)
              AND d.document_status = 'CURRENT'
              AND (d.expires_at IS NULL OR d.expires_at > CURRENT_TIMESTAMP)
              AND s.approval_status = 'APPROVED'
              AND (s.expires_at IS NULL OR s.expires_at > CURRENT_TIMESTAMP)
              AND ($2::text IS NULL OR d.language = $2)
              AND c.search_vector @@ websearch_to_tsquery('simple', $1)
            ORDER BY score DESC, c.id
            LIMIT $3
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(sql, query_text, language, top_k)
        return [
            RetrievedChunk(
                chunk_id=row["chunk_id"], document_id=str(row["document_id"]), source_id=row["source_id"],
                source_name=row["source_name"], canonical_url=row["canonical_url"], title=row["title"],
                content=row["content"], score=float(row["score"]), metadata=_metadata_as_dict(row["metadata"]),
            )
            for row in rows
        ]

    async def ingest_document(
        self,
        *,
        source_url: str,
        source_version: str,
        title: str | None,
        language: str,
        content_sha256: str,
        fetched_at: datetime,
        requested_by: str,
        chunks: Sequence[ChunkInsert],
    ) -> UUID:
        """Write one operator-triggered, approved-source document transactionally."""
        pool = await self._connection_pool()
        run_id = uuid4()
        document_id = uuid4()
        async with pool.acquire() as connection:
            source_id = await connection.fetchval(
                """SELECT id FROM rag.source
                   WHERE canonical_url = $1 AND approval_status = 'APPROVED'
                     AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)""",
                source_url,
            )
            if source_id is None:
                raise PermissionError("Only a current approved rag.source can be ingested.")
            await connection.execute(
                """INSERT INTO rag.ingestion_run (id, source_id, requested_by, actor_type, status, started_at)
                   VALUES ($1, $2, $3, 'OPERATOR', 'RUNNING', CURRENT_TIMESTAMP)""",
                run_id, source_id, requested_by,
            )
            try:
                async with connection.transaction():
                    # One approved source has one active document snapshot. Marking
                    # the prior snapshot superseded prevents stale chunks from being
                    # retrieved while retaining ingestion history for audit/eval.
                    await connection.execute(
                        """UPDATE rag.chunk c
                           SET chunk_status = 'SUPERSEDED'
                           FROM rag.document d
                           WHERE c.document_id = d.id
                             AND d.source_id = $1
                             AND c.chunk_status = 'CURRENT'""",
                        source_id,
                    )
                    await connection.execute(
                        """UPDATE rag.document
                           SET document_status = 'SUPERSEDED'
                           WHERE source_id = $1 AND document_status = 'CURRENT'""",
                        source_id,
                    )
                    await connection.execute(
                        """INSERT INTO rag.document
                           (id, source_id, ingestion_run_id, source_version, canonical_url, title, language,
                            document_status, content_sha256, fetched_at)
                           VALUES ($1, $2, $3, $4, $5, $6, $7, 'CURRENT', $8, $9)""",
                        document_id, source_id, run_id, source_version, source_url, title, language, content_sha256, fetched_at,
                    )
                    await connection.executemany(
                        """INSERT INTO rag.chunk
                           (id, document_id, ordinal, content, content_sha256, token_count, search_vector,
                            chunk_status)
                           VALUES ($1, $2, $3, $4, $5, $6, to_tsvector('simple', $4), 'CURRENT')""",
                        [
                            (
                                uuid4(), document_id, chunk.ordinal, chunk.content, chunk.content_sha256,
                                len(chunk.content.split()),
                            )
                            for chunk in chunks
                        ],
                    )
                    await connection.execute(
                        """UPDATE rag.source
                           SET current_version = $2, updated_at = CURRENT_TIMESTAMP
                           WHERE id = $1""",
                        source_id, source_version,
                    )
                await connection.execute(
                    """UPDATE rag.ingestion_run
                       SET status = 'SUCCEEDED', completed_at = CURRENT_TIMESTAMP,
                           document_count = 1, chunk_count = $2
                       WHERE id = $1""",
                    run_id, len(chunks),
                )
            except Exception as error:
                await connection.execute(
                    """UPDATE rag.ingestion_run
                       SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, failure_reason = $2
                       WHERE id = $1""",
                    run_id, str(error)[:1000],
                )
                raise
        return run_id
