from __future__ import annotations

import asyncpg

from app.rag.models import RetrievedChunk


class RagRepository:
    """Parameterized PostgreSQL full-text search over approved current chunks."""

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

    async def search(self, *, query_text: str, top_k: int) -> list[RetrievedChunk]:
        sql = """
            SELECT c.id::text AS chunk_id, c.document_id::text, d.source_id::text, s.source_name,
                   d.canonical_url, d.title, c.content,
                   ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', $1))::float AS score,
                   c.metadata
            FROM rag.chunk c
            JOIN rag.document d ON d.id = c.document_id
            JOIN rag.source s ON s.id = d.source_id
            WHERE s.approval_status = 'APPROVED'
              AND (s.expires_at IS NULL OR s.expires_at > CURRENT_TIMESTAMP)
              AND d.document_status = 'CURRENT'
              AND (d.expires_at IS NULL OR d.expires_at > CURRENT_TIMESTAMP)
              AND c.chunk_status = 'CURRENT'
              AND (c.expires_at IS NULL OR c.expires_at > CURRENT_TIMESTAMP)
              AND c.search_vector @@ websearch_to_tsquery('simple', $1)
            ORDER BY ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', $1)) DESC, c.id
            LIMIT $2
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(sql, query_text, top_k)
        return [
            RetrievedChunk(
                chunk_id=row["chunk_id"], document_id=str(row["document_id"]), source_id=row["source_id"],
                source_name=row["source_name"], canonical_url=row["canonical_url"], title=row["title"],
                content=row["content"], score=float(row["score"]), metadata=dict(row["metadata"] or {}),
            )
            for row in rows
        ]
