from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from uuid import UUID, uuid4

import asyncpg

from app.rag.models import RetrievedChunk


@dataclass(frozen=True)
class ChunkInsert:
    ordinal: int
    content: str
    content_sha256: str
    embedding: Sequence[float]


class RagRepository:
    """PostgreSQL-only hybrid retrieval over approved, current source versions."""

    def __init__(self, database_url: str) -> None:
        self._database_url = database_url
        self._pool: asyncpg.Pool | None = None

    async def _connection_pool(self) -> asyncpg.Pool:
        if self._pool is None:
            self._pool = await asyncpg.create_pool(self._database_url, min_size=1, max_size=4, command_timeout=8)
        return self._pool

    async def close(self) -> None:
        if self._pool is not None:
            await self._pool.close()
            self._pool = None

    async def hybrid_search(
        self,
        *,
        query_text: str,
        embedding: Sequence[float],
        top_k: int,
        vector_candidates: int,
        lexical_candidates: int,
        rrf_k: int,
        language: str | None = "ko",
    ) -> list[RetrievedChunk]:
        vector = "[" + ",".join(f"{value:.10g}" for value in embedding) + "]"
        sql = """
            WITH active_chunks AS (
                SELECT c.id, c.document_id, c.content, c.metadata, c.embedding, c.search_vector,
                       d.source_id, d.canonical_url, d.title, d.language, s.source_name
                FROM rag.chunk c
                JOIN rag.document d ON d.id = c.document_id
                JOIN rag.source s ON s.id = d.source_id
                WHERE s.approval_status = 'APPROVED'
                  AND (s.expires_at IS NULL OR s.expires_at > CURRENT_TIMESTAMP)
                  AND d.document_status = 'CURRENT'
                  AND (d.expires_at IS NULL OR d.expires_at > CURRENT_TIMESTAMP)
                  AND c.chunk_status = 'CURRENT'
                  AND (c.expires_at IS NULL OR c.expires_at > CURRENT_TIMESTAMP)
                  AND ($3::text IS NULL OR d.language = $3)
            ), vector_candidates AS (
                SELECT id, row_number() OVER (ORDER BY embedding <=> $1::vector) AS rank
                FROM active_chunks
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> $1::vector
                LIMIT $4
            ), lexical_candidates AS (
                SELECT id, row_number() OVER (
                    ORDER BY ts_rank_cd(search_vector, websearch_to_tsquery('simple', $2)) DESC
                ) AS rank
                FROM active_chunks
                WHERE search_vector @@ websearch_to_tsquery('simple', $2)
                ORDER BY ts_rank_cd(search_vector, websearch_to_tsquery('simple', $2)) DESC
                LIMIT $5
            ), fused AS (
                SELECT id, SUM(1.0 / ($6 + rank)) AS score
                FROM (
                    SELECT id, rank FROM vector_candidates
                    UNION ALL
                    SELECT id, rank FROM lexical_candidates
                ) candidates
                GROUP BY id
            )
            SELECT c.id::text AS chunk_id, c.document_id::text, c.source_id::text, c.source_name,
                   c.canonical_url, c.title, c.content, f.score, c.metadata
            FROM fused f
            JOIN active_chunks c ON c.id = f.id
            ORDER BY f.score DESC, c.id
            LIMIT $7
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(
                sql, vector, query_text, language, vector_candidates, lexical_candidates, rrf_k, top_k
            )
        return [
            RetrievedChunk(
                chunk_id=row["chunk_id"], document_id=str(row["document_id"]), source_id=row["source_id"],
                source_name=row["source_name"], canonical_url=row["canonical_url"], title=row["title"],
                content=row["content"], score=float(row["score"]), metadata=dict(row["metadata"] or {}),
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
        fetched_at: object,
        requested_by: str,
        embedding_model: str,
        embedding_version: str,
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
                """INSERT INTO rag.ingestion_run (id, source_id, requested_by, actor_type, status)
                   VALUES ($1, $2, $3, 'OPERATOR', 'RUNNING')""",
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
                            chunk_status, embedding_model, embedding_version, embedding_dimensions, embedding)
                           VALUES ($1, $2, $3, $4, $5, $6, to_tsvector('simple', $4), 'CURRENT', $7, $8, 1536, $9::vector)""",
                        [
                            (
                                uuid4(), document_id, chunk.ordinal, chunk.content, chunk.content_sha256,
                                len(chunk.content.split()), embedding_model, embedding_version,
                                "[" + ",".join(f"{value:.10g}" for value in chunk.embedding) + "]",
                            )
                            for chunk in chunks
                        ],
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
