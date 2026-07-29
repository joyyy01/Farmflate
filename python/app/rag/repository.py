from __future__ import annotations

import asyncio
import json
from math import isfinite
from time import monotonic
from hashlib import sha256
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID, uuid4

import asyncpg

from app.core.config import settings
from app.rag.models import RetrievedChunk
from app.rag.evaluation import EvaluationCase, EvaluationCorpusSnapshot
from app.rag.quality_gate import EvaluationRunMetric


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


def _json_string_tuple(value: object) -> tuple[str, ...]:
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            return ()
    if not isinstance(value, list):
        return ()
    return tuple(item for item in value if isinstance(item, str) and item.strip())


def _optional_float(value: object) -> float | None:
    return float(value) if value is not None else None


@dataclass(frozen=True)
class ChunkInsert:
    ordinal: int
    content: str
    content_sha256: str
    embedding: tuple[float, ...] | None = None
    embedding_model: str | None = None
    embedding_version: str | None = None
    embedding_dimensions: int | None = None


@dataclass(frozen=True)
class RetrievalExecution:
    chunks: list[RetrievedChunk]
    mode: str
    candidate_count: int


@dataclass(frozen=True)
class RetrievalQualityMetric:
    mode: str
    evaluation_count: int
    avg_recall_at_k: float | None
    avg_citation_precision: float | None
    request_count: int
    p50_latency_ms: float | None
    p95_latency_ms: float | None


@dataclass(frozen=True)
class AgentExecutionMetric:
    pipeline_version: str
    model_name: str
    execution_profile: str
    measurement_scope: str
    request_count: int
    completed_count: int
    needs_context_count: int
    failed_count: int
    completion_rate: float | None
    needs_context_rate: float | None
    p50_total_latency_ms: float | None
    p95_total_latency_ms: float | None
    p50_model_latency_ms: float | None
    p95_model_latency_ms: float | None
    avg_tool_call_count: float | None
    avg_citation_count: float | None
    terminal_reason_counts: dict[str, int]


@dataclass(frozen=True)
class SourceRegistration:
    source_id: str
    canonical_url: str
    approval_status: str
    embedding_egress_allowed: bool


class RagRepository:
    """PostgreSQL retrieval over approved, current source versions."""

    def __init__(self, database_url: str) -> None:
        self._database_url = database_url
        self._pool: asyncpg.Pool | None = None
        self._pool_lock = asyncio.Lock()
        self._semantic_available: bool | None = None
        self._semantic_checked_at = 0.0
        self._semantic_lock = asyncio.Lock()

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

    async def register_source(
        self,
        *,
        canonical_url: str,
        publisher: str,
        source_name: str,
        content_policy: str,
        actor: str,
        reason: str,
        expires_at: datetime | None = None,
    ) -> SourceRegistration:
        """Register a source as PENDING without granting retrieval access."""
        canonical_url, publisher, source_name, content_policy, actor, reason = self._validated_source_inputs(
            canonical_url, publisher, source_name, content_policy, actor, reason
        )
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            async with connection.transaction():
                row = await connection.fetchrow(
                    """INSERT INTO rag.source
                       (id, canonical_url, publisher, source_name, approval_status, expires_at, content_policy,
                        embedding_egress_allowed)
                       VALUES ($1, $2, $3, $4, 'PENDING', $5, $6, false)
                       ON CONFLICT (canonical_url) DO NOTHING
                       RETURNING id::text AS source_id, canonical_url, approval_status, embedding_egress_allowed""",
                    uuid4(), canonical_url, publisher, source_name, expires_at, content_policy,
                )
                if row is not None:
                    await connection.execute(
                        """INSERT INTO rag.source_audit_event (id, source_id, action, actor, reason)
                           VALUES ($1, $2::uuid, 'REGISTERED', $3, $4)""",
                        uuid4(), row["source_id"], actor, reason,
                    )
                    return self._source_registration(row)
                existing = await connection.fetchrow(
                    """SELECT id::text AS source_id, canonical_url, approval_status, embedding_egress_allowed
                       FROM rag.source WHERE canonical_url = $1""",
                    canonical_url,
                )
                if existing is None:
                    raise RuntimeError("RAG source registration could not be read after insert.")
                return self._source_registration(existing)

    async def review_source(
        self,
        *,
        canonical_url: str,
        action: str,
        actor: str,
        reason: str,
    ) -> SourceRegistration:
        """Apply an explicit approval decision and write an immutable audit event."""
        canonical_url = canonical_url.strip()
        action = action.strip().upper()
        actor = actor.strip()
        reason = reason.strip()
        if not canonical_url or action not in {"APPROVED", "REJECTED", "REVOKED"} or not actor or not reason:
            raise ValueError("Source review requires URL, action, actor, and reason.")
        if len(actor) > 240 or len(reason) > 1000:
            raise ValueError("Source review actor or reason exceeds its allowed length.")

        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            async with connection.transaction():
                row = await connection.fetchrow(
                    """SELECT id::text AS source_id FROM rag.source
                       WHERE canonical_url = $1 FOR UPDATE""",
                    canonical_url,
                )
                if row is None:
                    raise ValueError("RAG source does not exist.")
                if action == "APPROVED":
                    updated = await connection.fetchrow(
                        """UPDATE rag.source
                           SET approval_status = 'APPROVED', approved_by = $2, approved_at = CURRENT_TIMESTAMP,
                               updated_at = CURRENT_TIMESTAMP
                           WHERE id = $1::uuid
                           RETURNING id::text AS source_id, canonical_url, approval_status, embedding_egress_allowed""",
                        row["source_id"], actor,
                    )
                else:
                    updated = await connection.fetchrow(
                        """UPDATE rag.source
                           SET approval_status = $2, approved_by = NULL, approved_at = NULL,
                               updated_at = CURRENT_TIMESTAMP
                           WHERE id = $1::uuid
                           RETURNING id::text AS source_id, canonical_url, approval_status, embedding_egress_allowed""",
                        row["source_id"], action,
                    )
                await connection.execute(
                    """INSERT INTO rag.source_audit_event (id, source_id, action, actor, reason)
                       VALUES ($1, $2::uuid, $3, $4, $5)""",
                    uuid4(), row["source_id"], action, actor, reason,
                )
                if updated is None:
                    raise RuntimeError("RAG source review could not be read after update.")
                return self._source_registration(updated)

    async def set_embedding_egress_policy(
        self,
        *,
        canonical_url: str,
        allowed: bool,
        actor: str,
        reason: str,
    ) -> SourceRegistration:
        """Permit external embedding only for an explicitly approved source."""
        canonical_url, actor, reason = (value.strip() for value in (canonical_url, actor, reason))
        if not canonical_url or not actor or not reason:
            raise ValueError("Embedding egress policy requires URL, actor, and reason.")
        if len(actor) > 240 or len(reason) > 1000:
            raise ValueError("Embedding egress actor or reason exceeds its allowed length.")

        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            async with connection.transaction():
                source = await connection.fetchrow(
                    """SELECT id::text AS source_id, approval_status FROM rag.source
                       WHERE canonical_url = $1 FOR UPDATE""",
                    canonical_url,
                )
                if source is None:
                    raise ValueError("RAG source does not exist.")
                if allowed and source["approval_status"] != "APPROVED":
                    raise PermissionError("Only an approved source may allow external embedding.")
                updated = await connection.fetchrow(
                    """UPDATE rag.source
                       SET embedding_egress_allowed = $2, updated_at = CURRENT_TIMESTAMP
                       WHERE id = $1::uuid
                       RETURNING id::text AS source_id, canonical_url, approval_status, embedding_egress_allowed""",
                    source["source_id"], allowed,
                )
                await connection.execute(
                    """INSERT INTO rag.source_audit_event (id, source_id, action, actor, reason)
                       VALUES ($1, $2::uuid, $3, $4, $5)""",
                    uuid4(), source["source_id"],
                    "EMBEDDING_EGRESS_ALLOWED" if allowed else "EMBEDDING_EGRESS_REVOKED",
                    actor, reason,
                )
                if updated is None:
                    raise RuntimeError("RAG embedding egress policy could not be read after update.")
                return self._source_registration(updated)

    async def embedding_egress_allowed(self, *, canonical_url: str) -> bool:
        """Check the current source policy before any document leaves PostgreSQL."""
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            return bool(await connection.fetchval(
                """SELECT embedding_egress_allowed FROM rag.source
                   WHERE canonical_url = $1 AND approval_status = 'APPROVED'
                     AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)""",
                canonical_url.strip(),
            ))

    @staticmethod
    def _validated_source_inputs(
        canonical_url: str,
        publisher: str,
        source_name: str,
        content_policy: str,
        actor: str,
        reason: str,
    ) -> tuple[str, str, str, str, str, str]:
        values = tuple(value.strip() for value in (canonical_url, publisher, source_name, content_policy, actor, reason))
        if not all(values):
            raise ValueError("Source registration requires URL, publisher, name, policy, actor, and reason.")
        if len(values[2]) > 240 or len(values[4]) > 240 or len(values[5]) > 1000:
            raise ValueError("Source registration input exceeds its allowed length.")
        return values

    @staticmethod
    def _source_registration(row: asyncpg.Record) -> SourceRegistration:
        return SourceRegistration(
            source_id=str(row["source_id"]),
            canonical_url=str(row["canonical_url"]),
            approval_status=str(row["approval_status"]),
            embedding_egress_allowed=bool(row["embedding_egress_allowed"]),
        )

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
                   ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g'))) AS score, c.metadata
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
               AND c.search_vector @@ websearch_to_tsquery('simple', regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g'))
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

    async def search_hybrid(
        self,
        *,
        query_text: str,
        query_embedding: Sequence[float],
        embedding_model: str,
        embedding_dimensions: int,
        top_k: int,
        language: str | None = "ko",
        lexical_candidate_limit: int = 24,
        semantic_candidate_limit: int = 24,
        rrf_k: int = 60,
    ) -> RetrievalExecution:
        """Fuse PostgreSQL FTS and pgvector candidates with reciprocal rank fusion."""
        if not await self.semantic_search_available():
            chunks = await self.search(query_text=query_text, top_k=top_k, language=language)
            return RetrievalExecution(chunks=chunks, mode="lexical", candidate_count=len(chunks))

        vector = self._vector_literal(query_embedding, embedding_dimensions)
        sql = """
            WITH lexical AS (
                SELECT c.id,
                       row_number() OVER (
                            ORDER BY ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g'))) DESC, c.id
                       ) AS lexical_rank
                FROM rag.chunk c
                JOIN rag.document d ON d.id = c.document_id
                JOIN rag.source s ON s.id = d.source_id
                WHERE c.chunk_status = 'CURRENT'
                  AND (c.expires_at IS NULL OR c.expires_at > CURRENT_TIMESTAMP)
                  AND d.document_status = 'CURRENT'
                  AND (d.expires_at IS NULL OR d.expires_at > CURRENT_TIMESTAMP)
                  AND s.approval_status = 'APPROVED'
                  AND (s.expires_at IS NULL OR s.expires_at > CURRENT_TIMESTAMP)
                  AND ($3::text IS NULL OR d.language = $3)
                   AND c.search_vector @@ websearch_to_tsquery('simple', regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g'))
                 ORDER BY ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', regexp_replace(trim($1), '[[:space:]]+', ' OR ', 'g'))) DESC, c.id
                LIMIT $4
            ),
            semantic AS (
                SELECT c.id,
                       row_number() OVER (ORDER BY c.embedding <=> $2::vector, c.id) AS semantic_rank
                FROM rag.chunk c
                JOIN rag.document d ON d.id = c.document_id
                JOIN rag.source s ON s.id = d.source_id
                WHERE c.chunk_status = 'CURRENT'
                  AND (c.expires_at IS NULL OR c.expires_at > CURRENT_TIMESTAMP)
                  AND d.document_status = 'CURRENT'
                  AND (d.expires_at IS NULL OR d.expires_at > CURRENT_TIMESTAMP)
                  AND s.approval_status = 'APPROVED'
                  AND (s.expires_at IS NULL OR s.expires_at > CURRENT_TIMESTAMP)
                  AND ($3::text IS NULL OR d.language = $3)
                  AND c.embedding IS NOT NULL
                  AND c.embedding_model = $8
                  AND c.embedding_dimensions = $9
                ORDER BY c.embedding <=> $2::vector, c.id
                LIMIT $5
            ),
            ranked AS (
                SELECT id, lexical_rank, NULL::bigint AS semantic_rank FROM lexical
                UNION ALL
                SELECT id, NULL::bigint AS lexical_rank, semantic_rank FROM semantic
            ),
            fused AS (
                SELECT id, min(lexical_rank) AS lexical_rank, min(semantic_rank) AS semantic_rank
                FROM ranked
                GROUP BY id
            )
            SELECT c.id::text AS chunk_id, c.document_id::text, d.source_id::text, s.source_name,
                   d.canonical_url, d.title, c.content,
                   (COALESCE(1.0 / ($7 + lexical_rank), 0) +
                    COALESCE(1.0 / ($7 + semantic_rank), 0)) AS score,
                   c.metadata, fused.lexical_rank, fused.semantic_rank
            FROM fused
            JOIN rag.chunk c ON c.id = fused.id
            JOIN rag.document d ON d.id = c.document_id
            JOIN rag.source s ON s.id = d.source_id
            ORDER BY score DESC, c.id
            LIMIT $6
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(
                sql,
                query_text,
                vector,
                language,
                lexical_candidate_limit,
                semantic_candidate_limit,
                top_k,
                rrf_k,
                embedding_model,
                embedding_dimensions,
            )
        chunks = [
            RetrievedChunk(
                chunk_id=row["chunk_id"],
                document_id=str(row["document_id"]),
                source_id=row["source_id"],
                source_name=row["source_name"],
                canonical_url=row["canonical_url"],
                title=row["title"],
                content=row["content"],
                score=float(row["score"]),
                metadata=_metadata_as_dict(row["metadata"]),
                retrieval_paths=tuple(
                    path
                    for path, rank in (("lexical", row.get("lexical_rank")), ("semantic", row.get("semantic_rank")))
                    if rank is not None
                ),
            )
            for row in rows
        ]
        return RetrievalExecution(chunks=chunks, mode="hybrid", candidate_count=len(chunks))

    async def semantic_search_available(self) -> bool:
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            return await self._semantic_search_available_on(connection)

    async def _semantic_search_available_on(self, connection: asyncpg.Connection) -> bool:
        if self._semantic_available is not None and self._semantic_cache_is_fresh():
            return self._semantic_available
        async with self._semantic_lock:
            if self._semantic_available is not None and self._semantic_cache_is_fresh():
                return self._semantic_available
            sql = """
                SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')
                   AND EXISTS (
                       SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'rag' AND table_name = 'chunk' AND column_name = 'embedding'
                   )
            """
            self._semantic_available = bool(await connection.fetchval(sql))
            self._semantic_checked_at = monotonic()
        return self._semantic_available

    def _semantic_cache_is_fresh(self) -> bool:
        return monotonic() - self._semantic_checked_at < settings.RAG_SEMANTIC_AVAILABILITY_TTL_SECONDS

    @staticmethod
    def _vector_literal(values: Sequence[float], expected_dimensions: int) -> str:
        if len(values) != expected_dimensions or not all(isfinite(float(value)) for value in values):
            raise ValueError("Embedding vector does not satisfy the configured dimension contract.")
        return "[" + ",".join(format(float(value), ".9g") for value in values) + "]"

    async def ingest_document(
        self,
        *,
        source_url: str,
        source_version: str,
        external_id: str | None = None,
        title: str | None,
        language: str,
        content_sha256: str,
        fetched_at: datetime,
        requested_by: str,
        chunks: Sequence[ChunkInsert],
        embedding_status: str = "NOT_REQUESTED",
        embedding_model: str | None = None,
        embedding_dimensions: int | None = None,
        embedding_failure_reason: str | None = None,
    ) -> UUID:
        """Write one operator-triggered, approved-source document transactionally."""
        publication_id = (external_id or source_version).strip()
        if not publication_id or len(publication_id) > 500:
            raise ValueError("Document external_id is required and bounded.")
        pool = await self._connection_pool()
        run_id = uuid4()
        async with pool.acquire() as connection:
            source_id = await connection.fetchval(
                """SELECT id FROM rag.source
                   WHERE canonical_url = $1 AND approval_status = 'APPROVED'
                     AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)""",
                source_url,
            )
            if source_id is None:
                raise PermissionError("Only a current approved rag.source can be ingested.")
            source_allows_embedding_egress = bool(await connection.fetchval(
                """SELECT embedding_egress_allowed FROM rag.source
                   WHERE id = $1 AND approval_status = 'APPROVED'
                     AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)""",
                source_id,
            ))
            embedding_ready = bool(chunks) and all(
                chunk.embedding is not None
                and chunk.embedding_model is not None
                and chunk.embedding_version is not None
                and chunk.embedding_dimensions is not None
                for chunk in chunks
            )
            write_embeddings = (
                embedding_status == "READY"
                and source_allows_embedding_egress
                and embedding_ready
                and await self._semantic_search_available_on(connection)
            )
            effective_embedding_status = embedding_status
            effective_failure_reason = embedding_failure_reason
            if embedding_status == "READY" and not source_allows_embedding_egress:
                effective_embedding_status = "POLICY_DENIED"
                effective_failure_reason = "Source policy does not permit external embedding."
            elif embedding_status == "READY" and not write_embeddings:
                effective_embedding_status = "UNAVAILABLE"
                effective_failure_reason = "pgvector extension or embedding schema is unavailable."
            await connection.execute(
                """INSERT INTO rag.ingestion_run
                   (id, source_id, requested_by, actor_type, status, started_at, embedding_status,
                    embedding_model, embedding_dimensions, embedding_failure_reason)
                   VALUES ($1, $2, $3, 'OPERATOR', 'RUNNING', CURRENT_TIMESTAMP, $4, $5, $6, $7)""",
                run_id, source_id, requested_by, effective_embedding_status, embedding_model,
                embedding_dimensions, effective_failure_reason,
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
                              AND d.external_id = $2
                              AND c.chunk_status = 'CURRENT'""",
                        source_id, publication_id,
                    )
                    await connection.execute(
                        """UPDATE rag.document
                           SET document_status = 'SUPERSEDED'
                            WHERE source_id = $1 AND external_id = $2 AND document_status = 'CURRENT'""",
                        source_id, publication_id,
                    )
                    document_id = await connection.fetchval(
                        """INSERT INTO rag.document
                            (id, source_id, ingestion_run_id, source_version, external_id, canonical_url, title, language,
                             document_status, content_sha256, fetched_at)
                           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'CURRENT', $9, $10)
                           ON CONFLICT (source_id, source_version, content_sha256) DO UPDATE
                           SET ingestion_run_id = EXCLUDED.ingestion_run_id,
                               external_id = EXCLUDED.external_id,
                               canonical_url = EXCLUDED.canonical_url,
                               title = EXCLUDED.title,
                               language = EXCLUDED.language,
                               document_status = 'CURRENT',
                               fetched_at = EXCLUDED.fetched_at,
                               updated_at = CURRENT_TIMESTAMP
                           RETURNING id""",
                        uuid4(), source_id, run_id, source_version, publication_id, source_url, title, language,
                        content_sha256, fetched_at,
                    )
                    if document_id is None:
                        raise RuntimeError("Document upsert did not return an identifier.")
                    if write_embeddings:
                        await connection.executemany(
                            """INSERT INTO rag.chunk
                               (id, document_id, ordinal, content, content_sha256, token_count, search_vector,
                                chunk_status, embedding_model, embedding_version, embedding_dimensions, embedding)
                               VALUES ($1, $2, $3, $4, $5, $6, to_tsvector('simple', $4), 'CURRENT',
                                       $7, $8, $9, $10::vector)
                               ON CONFLICT (document_id, ordinal, content_sha256) DO UPDATE
                               SET content = EXCLUDED.content,
                                   token_count = EXCLUDED.token_count,
                                   search_vector = EXCLUDED.search_vector,
                                   chunk_status = 'CURRENT',
                                   embedding_model = EXCLUDED.embedding_model,
                                   embedding_version = EXCLUDED.embedding_version,
                                   embedding_dimensions = EXCLUDED.embedding_dimensions,
                                   embedding = EXCLUDED.embedding""",
                            [
                                (
                                    uuid4(), document_id, chunk.ordinal, chunk.content, chunk.content_sha256,
                                    len(chunk.content.split()), chunk.embedding_model, chunk.embedding_version,
                                    chunk.embedding_dimensions,
                                    self._vector_literal(chunk.embedding or (), chunk.embedding_dimensions or 0),
                                )
                                for chunk in chunks
                            ],
                        )
                    else:
                        await connection.executemany(
                            """INSERT INTO rag.chunk
                               (id, document_id, ordinal, content, content_sha256, token_count, search_vector,
                                chunk_status)
                               VALUES ($1, $2, $3, $4, $5, $6, to_tsvector('simple', $4), 'CURRENT')
                               ON CONFLICT (document_id, ordinal, content_sha256) DO UPDATE
                                   SET content = EXCLUDED.content,
                                   token_count = EXCLUDED.token_count,
                                   search_vector = EXCLUDED.search_vector,
                                   chunk_status = 'CURRENT'""",
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

    async def start_evaluation_run(
        self,
        *,
        dataset_key: str,
        dataset_version: str,
        retrieval_mode: str,
        retrieval_config: dict[str, object],
        requested_by: str,
        corpus_snapshot: EvaluationCorpusSnapshot,
    ) -> str:
        if retrieval_mode not in {"lexical", "hybrid"}:
            raise ValueError("Evaluation retrieval mode must be lexical or hybrid.")
        values = (dataset_key.strip(), dataset_version.strip(), requested_by.strip())
        if not all(values) or any(len(value) > 240 for value in values):
            raise ValueError("Evaluation dataset and requester are required and bounded.")
        run_id = uuid4()
        sql = """
            INSERT INTO rag.eval_run
                (id, dataset_key, dataset_version, retrieval_mode, retrieval_config, requested_by, status,
                 corpus_fingerprint, evaluation_origin, corpus_document_count, corpus_chunk_count)
            VALUES ($1, $2, $3, $4, $5::jsonb, $6, 'RUNNING', $7, $8, $9, $10)
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            await connection.execute(
                sql, run_id, values[0], values[1], retrieval_mode,
                json.dumps(retrieval_config, ensure_ascii=False), values[2], corpus_snapshot.fingerprint,
                corpus_snapshot.evaluation_origin, corpus_snapshot.document_count, corpus_snapshot.chunk_count,
            )
        return str(run_id)

    async def evaluation_corpus_snapshot(
        self,
        *,
        dataset_key: str,
        dataset_version: str,
    ) -> EvaluationCorpusSnapshot:
        cases = await self.list_active_eval_cases(dataset_key=dataset_key, dataset_version=dataset_version)
        if not cases:
            raise ValueError("Evaluation requires at least one active case.")
        origins = {case.evaluation_origin for case in cases}
        if len(origins) != 1:
            raise ValueError("Evaluation cases must have one shared origin.")
        expected_chunk_ids = sorted({chunk_id for case in cases for chunk_id in case.expected_chunk_ids})
        if not expected_chunk_ids:
            raise ValueError("Evaluation cases require expected current chunks.")
        sql = """
            SELECT c.id::text AS chunk_id, c.document_id::text AS document_id, c.content_sha256
            FROM rag.chunk c
            JOIN rag.document d ON d.id = c.document_id
            JOIN rag.source s ON s.id = d.source_id
            WHERE c.id::text = ANY($1::text[])
              AND c.chunk_status = 'CURRENT' AND d.document_status = 'CURRENT'
              AND s.approval_status = 'APPROVED'
            ORDER BY c.id
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(sql, expected_chunk_ids)
        current = {str(row["chunk_id"]): str(row["content_sha256"]) for row in rows}
        stale = sorted(set(expected_chunk_ids) - set(current))
        if stale:
            raise ValueError("Evaluation cases reference stale or unavailable chunks.")
        digest = sha256()
        for chunk_id in expected_chunk_ids:
            digest.update(f"{chunk_id}:{current[chunk_id]}\n".encode("utf-8"))
        return EvaluationCorpusSnapshot(
            fingerprint=digest.hexdigest(),
            document_count=len({str(row["document_id"]) for row in rows}),
            chunk_count=len(rows),
            evaluation_origin=origins.pop(),
        )

    async def current_chunks_for_source(self, *, canonical_url: str) -> list[tuple[str, str]]:
        sql = """
            SELECT c.id::text AS chunk_id, c.content
            FROM rag.chunk c
            JOIN rag.document d ON d.id = c.document_id
            JOIN rag.source s ON s.id = d.source_id
            WHERE s.canonical_url = $1 AND s.approval_status = 'APPROVED'
              AND d.document_status = 'CURRENT' AND c.chunk_status = 'CURRENT'
            ORDER BY d.fetched_at DESC, d.external_id, c.ordinal
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(sql, canonical_url.strip())
        return [(str(row["chunk_id"]), str(row["content"])) for row in rows]

    async def upsert_auto_evaluation_cases(
        self,
        *,
        dataset_key: str,
        dataset_version: str,
        cases: Sequence[tuple[str, str, str]],
    ) -> int:
        if not cases:
            raise ValueError("Automatic evaluation requires at least one case.")
        values = (dataset_key.strip(), dataset_version.strip())
        if not all(values):
            raise ValueError("Automatic evaluation dataset key and version are required.")
        sql = """
            INSERT INTO rag.eval_case
                (id, case_key, query_text, language, expected_chunk_ids, expected_citations,
                 status, dataset_key, dataset_version, evaluation_origin)
            VALUES ($1, $2, $3, 'ko', $4::jsonb, $5::jsonb,
                    'ACTIVE', $6, $7, 'AUTO_GENERATED')
            ON CONFLICT (case_key) DO UPDATE
            SET query_text = EXCLUDED.query_text,
                expected_chunk_ids = EXCLUDED.expected_chunk_ids,
                expected_citations = EXCLUDED.expected_citations,
                status = 'ACTIVE', dataset_key = EXCLUDED.dataset_key,
                dataset_version = EXCLUDED.dataset_version,
                evaluation_origin = 'AUTO_GENERATED', updated_at = CURRENT_TIMESTAMP
        """
        arguments = [
            (uuid4(), case_key, query, json.dumps([chunk_id]), json.dumps([f"rag:{chunk_id}"]), values[0], values[1])
            for case_key, query, chunk_id in cases
        ]
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            await connection.executemany(sql, arguments)
        return len(arguments)

    async def complete_evaluation_run(
        self,
        *,
        run_id: str,
        case_count: int,
        failure_reason: str | None = None,
    ) -> None:
        if case_count < 0:
            raise ValueError("Evaluation case count cannot be negative.")
        status = "FAILED" if failure_reason else "SUCCEEDED"
        sql = """
            UPDATE rag.eval_run
            SET status = $2, case_count = $3, completed_at = CURRENT_TIMESTAMP, failure_reason = $4
            WHERE id = $1::uuid AND status = 'RUNNING'
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            changed = await connection.execute(sql, run_id, status, case_count, failure_reason)
        if changed != "UPDATE 1":
            raise ValueError("Evaluation run does not exist or is already complete.")

    async def list_active_eval_cases(self, *, dataset_key: str, dataset_version: str) -> list[EvaluationCase]:
        sql = """
            SELECT id::text AS case_id, case_key, query_text, language, expected_chunk_ids, expected_citations,
                   evaluation_origin
            FROM rag.eval_case
            WHERE status = 'ACTIVE' AND dataset_key = $1 AND dataset_version = $2
            ORDER BY case_key
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(sql, dataset_key.strip(), dataset_version.strip())
        return [
            EvaluationCase(
                case_id=row["case_id"],
                case_key=row["case_key"],
                query_text=row["query_text"],
                language=row["language"],
                expected_chunk_ids=_json_string_tuple(row["expected_chunk_ids"]),
                expected_citations=_json_string_tuple(row["expected_citations"]),
                evaluation_origin=str(row["evaluation_origin"]),
            )
            for row in rows
        ]

    async def record_eval_result(
        self,
        *,
        case_id: str,
        evaluation_run_id: str | None,
        embedding_model: str,
        retrieval_mode: str,
        result_status: str,
        recall_at_k: float | None,
        citation_precision: float | None,
        reciprocal_rank: float | None,
        first_relevant_rank: int | None,
        latency_ms: int | None,
        evidence: list[dict[str, str]],
        retrieval_config: dict[str, object],
        failure_reason: str | None = None,
    ) -> None:
        sql = """
            INSERT INTO rag.eval_result
                 (id, eval_case_id, eval_run_id, evaluated_at, embedding_model, retrieval_config, result_status,
                  recall_at_k, citation_precision, reciprocal_rank, first_relevant_rank, latency_ms, evidence,
                  failure_reason, retrieval_mode)
            VALUES ($1, $2::uuid, $3::uuid, CURRENT_TIMESTAMP, $4, $5::jsonb, $6, $7, $8, $9, $10, $11, $12::jsonb, $13, $14)
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            await connection.execute(
                sql,
                uuid4(),
                case_id,
                evaluation_run_id,
                embedding_model,
                json.dumps(retrieval_config, ensure_ascii=False),
                result_status,
                recall_at_k,
                citation_precision,
                reciprocal_rank,
                first_relevant_rank,
                latency_ms,
                json.dumps(evidence, ensure_ascii=False),
                failure_reason,
                retrieval_mode,
            )

    async def evaluation_run_metrics(self, *, run_id: str) -> EvaluationRunMetric:
        sql = """
            SELECT run.id::text AS run_id, run.dataset_key, run.dataset_version, run.retrieval_mode,
                   run.corpus_fingerprint, run.evaluation_origin,
                   run.case_count,
                   count(result.id) FILTER (WHERE result.result_status IN ('PASSED', 'FAILED'))::integer AS completed_count,
                   count(result.id) FILTER (WHERE result.result_status = 'ERROR')::integer AS error_count,
                    avg(result.recall_at_k)::double precision AS avg_recall_at_k,
                    avg(result.citation_precision)::double precision AS avg_citation_precision,
                    avg(result.reciprocal_rank)::double precision AS avg_reciprocal_rank,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY result.latency_ms) AS p95_latency_ms
            FROM rag.eval_run run
            LEFT JOIN rag.eval_result result ON result.eval_run_id = run.id
            WHERE run.id = $1::uuid AND run.status = 'SUCCEEDED'
            GROUP BY run.id
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            row = await connection.fetchrow(sql, run_id)
        if row is None:
            raise ValueError("A completed evaluation run is required for comparison.")
        return EvaluationRunMetric(
            run_id=str(row["run_id"]),
            dataset_key=str(row["dataset_key"]),
            dataset_version=str(row["dataset_version"]),
            retrieval_mode=str(row["retrieval_mode"]),
            case_count=int(row["case_count"]),
            completed_count=int(row["completed_count"]),
            error_count=int(row["error_count"]),
            avg_recall_at_k=_optional_float(row["avg_recall_at_k"]),
            avg_citation_precision=_optional_float(row["avg_citation_precision"]),
            avg_reciprocal_rank=_optional_float(row["avg_reciprocal_rank"]),
            p95_latency_ms=_optional_float(row["p95_latency_ms"]),
            corpus_fingerprint=str(row["corpus_fingerprint"]),
            evaluation_origin=str(row["evaluation_origin"]),
        )

    async def record_retrieval_trace(
        self,
        *,
        request_id: str,
        query_text: str,
        retrieval_mode: str,
        retrieval_status: str,
        candidate_count: int,
        returned_chunk_ids: list[str],
        latency_ms: int,
    ) -> None:
        sql = """
            INSERT INTO rag.retrieval_trace
                (id, request_id, query_sha256, retrieval_mode, retrieval_status, candidate_count,
                 returned_chunk_ids, latency_ms)
            VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, $8)
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            await connection.execute(
                sql,
                uuid4(),
                request_id[:80],
                sha256(query_text.encode("utf-8")).hexdigest(),
                retrieval_mode,
                retrieval_status,
                candidate_count,
                json.dumps(returned_chunk_ids, ensure_ascii=False),
                latency_ms,
            )

    async def record_agent_execution(
        self,
        *,
        request_id: str,
        pipeline_version: str,
        model_name: str,
        execution_profile: str,
        measurement_scope: str,
        terminal_status: str,
        terminal_reason: str,
        model_turn_count: int,
        tool_call_count: int,
        tool_non_success_count: int,
        citation_count: int,
        answer_char_count: int,
        total_latency_ms: int,
        model_latency_ms: int,
        tool_latency_ms: int,
        tool_statuses: list[str],
    ) -> None:
        sql = """
            INSERT INTO rag.agent_execution_trace
                (id, request_id, pipeline_version, model_name, execution_profile, measurement_scope, terminal_status, terminal_reason, model_turn_count, tool_call_count,
                 tool_non_success_count, citation_count, answer_char_count, total_latency_ms,
                 model_latency_ms, tool_latency_ms, tool_statuses)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17::jsonb)
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            await connection.execute(
                sql,
                uuid4(),
                request_id[:80],
                pipeline_version[:64],
                model_name[:128],
                execution_profile[:64],
                measurement_scope[:32],
                terminal_status,
                terminal_reason[:64],
                max(0, model_turn_count),
                max(0, tool_call_count),
                max(0, tool_non_success_count),
                max(0, citation_count),
                max(0, answer_char_count),
                max(0, total_latency_ms),
                max(0, model_latency_ms),
                max(0, tool_latency_ms),
                json.dumps([status[:32] for status in tool_statuses], ensure_ascii=False),
            )

    async def enable_semantic_retrieval(self) -> bool:
        """Run the operator-controlled PostgreSQL activation after pgvector installation."""
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            enabled = bool(await connection.fetchval("SELECT rag.enable_semantic_retrieval()"))
        self._semantic_available = enabled
        return enabled

    async def retrieval_quality_metrics(self, *, days: int) -> list[RetrievalQualityMetric]:
        if not 1 <= days <= 365:
            raise ValueError("Metric window must be between 1 and 365 days.")
        sql = """
            WITH evaluation AS (
                SELECT retrieval_mode,
                       count(*)::integer AS evaluation_count,
                       avg(recall_at_k)::double precision AS avg_recall_at_k,
                       avg(citation_precision)::double precision AS avg_citation_precision
                FROM rag.eval_result
                WHERE evaluated_at >= CURRENT_TIMESTAMP - ($1::integer * INTERVAL '1 day')
                GROUP BY retrieval_mode
            ), runtime AS (
                SELECT retrieval_mode,
                       count(*)::integer AS request_count,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY latency_ms) AS p50_latency_ms,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_latency_ms
                FROM rag.retrieval_trace
                WHERE created_at >= CURRENT_TIMESTAMP - ($1::integer * INTERVAL '1 day')
                GROUP BY retrieval_mode
            )
            SELECT COALESCE(evaluation.retrieval_mode, runtime.retrieval_mode) AS retrieval_mode,
                   COALESCE(evaluation.evaluation_count, 0) AS evaluation_count,
                   evaluation.avg_recall_at_k, evaluation.avg_citation_precision,
                   COALESCE(runtime.request_count, 0) AS request_count,
                   runtime.p50_latency_ms, runtime.p95_latency_ms
            FROM evaluation
            FULL OUTER JOIN runtime ON runtime.retrieval_mode = evaluation.retrieval_mode
            ORDER BY retrieval_mode
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            rows = await connection.fetch(sql, days)
        return [
            RetrievalQualityMetric(
                mode=row["retrieval_mode"],
                evaluation_count=int(row["evaluation_count"]),
                avg_recall_at_k=_optional_float(row["avg_recall_at_k"]),
                avg_citation_precision=_optional_float(row["avg_citation_precision"]),
                request_count=int(row["request_count"]),
                p50_latency_ms=_optional_float(row["p50_latency_ms"]),
                p95_latency_ms=_optional_float(row["p95_latency_ms"]),
            )
            for row in rows
        ]

    async def agent_execution_metrics(
        self,
        *,
        days: int,
        pipeline_version: str,
        model_name: str,
        execution_profile: str,
        measurement_scope: str,
    ) -> AgentExecutionMetric:
        if not 1 <= days <= 365:
            raise ValueError("Metric window must be between 1 and 365 days.")
        if not pipeline_version or not model_name or not execution_profile or not measurement_scope:
            raise ValueError("Agent execution metrics require a non-blank execution profile.")
        sql = """
            SELECT count(*)::integer AS request_count,
                   count(*) FILTER (WHERE terminal_status = 'completed')::integer AS completed_count,
                   count(*) FILTER (WHERE terminal_status = 'needs_context')::integer AS needs_context_count,
                   count(*) FILTER (WHERE terminal_status = 'failed')::integer AS failed_count,
                   avg((terminal_status = 'completed')::integer)::double precision AS completion_rate,
                   avg((terminal_status = 'needs_context')::integer)::double precision AS needs_context_rate,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY total_latency_ms) AS p50_total_latency_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY total_latency_ms) AS p95_total_latency_ms,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY model_latency_ms) AS p50_model_latency_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY model_latency_ms) AS p95_model_latency_ms,
                   avg(tool_call_count)::double precision AS avg_tool_call_count,
                   avg(citation_count)::double precision AS avg_citation_count
            FROM rag.agent_execution_trace
            WHERE created_at >= CURRENT_TIMESTAMP - ($1::integer * INTERVAL '1 day')
              AND pipeline_version = $2
              AND model_name = $3
              AND execution_profile = $4
              AND measurement_scope = $5
        """
        pool = await self._connection_pool()
        async with pool.acquire() as connection:
            row = await connection.fetchrow(sql, days, pipeline_version, model_name, execution_profile, measurement_scope)
            reason_rows = await connection.fetch(
                """
                SELECT terminal_reason, count(*)::integer AS request_count
                FROM rag.agent_execution_trace
                WHERE created_at >= CURRENT_TIMESTAMP - ($1::integer * INTERVAL '1 day')
                  AND pipeline_version = $2
                  AND model_name = $3
                  AND execution_profile = $4
                  AND measurement_scope = $5
                GROUP BY terminal_reason
                ORDER BY terminal_reason
                """,
                days,
                pipeline_version,
                model_name,
                execution_profile,
                measurement_scope,
            )
        terminal_reason_counts = {
            str(reason_row["terminal_reason"]): int(reason_row["request_count"])
            for reason_row in reason_rows
        }
        if row is None:
            return AgentExecutionMetric(pipeline_version, model_name, execution_profile, measurement_scope, 0, 0, 0, 0, None, None, None, None, None, None, None, None, {})
        return AgentExecutionMetric(
            pipeline_version=pipeline_version,
            model_name=model_name,
            execution_profile=execution_profile,
            measurement_scope=measurement_scope,
            request_count=int(row["request_count"]),
            completed_count=int(row["completed_count"]),
            needs_context_count=int(row["needs_context_count"]),
            failed_count=int(row["failed_count"]),
            completion_rate=_optional_float(row["completion_rate"]),
            needs_context_rate=_optional_float(row["needs_context_rate"]),
            p50_total_latency_ms=_optional_float(row["p50_total_latency_ms"]),
            p95_total_latency_ms=_optional_float(row["p95_total_latency_ms"]),
            p50_model_latency_ms=_optional_float(row["p50_model_latency_ms"]),
            p95_model_latency_ms=_optional_float(row["p95_model_latency_ms"]),
            avg_tool_call_count=_optional_float(row["avg_tool_call_count"]),
            avg_citation_count=_optional_float(row["avg_citation_count"]),
            terminal_reason_counts=terminal_reason_counts,
        )
