from __future__ import annotations

import logging
from time import perf_counter

from app.agent.contracts import AgentExecutionTelemetry
from app.agent.execution_profile import configured_execution_profile
from app.core.config import settings
from app.rag.embeddings import EmbeddingProvider, EmbeddingUnavailable, configured_embedding_provider
from app.rag.models import RetrievalResult
from app.rag.repository import RagRepository


logger = logging.getLogger(__name__)


class _UseConfiguredRepository:
    pass


_USE_CONFIGURED_REPOSITORY = _UseConfiguredRepository()


class PostgresRagRetriever:
    """Approved-knowledge retrieval backed by PostgreSQL FTS and optional pgvector."""

    def __init__(
        self,
        repository: RagRepository | None | _UseConfiguredRepository = _USE_CONFIGURED_REPOSITORY,
        embedding_provider: EmbeddingProvider | None = None,
        use_configured_embedding_provider: bool = True,
    ) -> None:
        self._repository = (
            RagRepository(settings.RAG_DATABASE_URL) if settings.RAG_DATABASE_URL else None
        ) if repository is _USE_CONFIGURED_REPOSITORY else repository
        self._embedding_provider = (
            embedding_provider
            if embedding_provider is not None
            else configured_embedding_provider() if use_configured_embedding_provider else None
        )

    @property
    def available(self) -> bool:
        return self._repository is not None

    async def retrieve(
        self,
        query: str,
        *,
        language: str | None = "ko",
        request_id: str | None = None,
    ) -> RetrievalResult:
        normalized = query.strip()
        if not self.available or not normalized:
            return RetrievalResult(chunks=[], query=normalized, insufficient_evidence=True)
        started_at = perf_counter()
        if self._embedding_provider is not None:
            try:
                embedding = await self._embedding_provider.embed_query(normalized)
            except EmbeddingUnavailable:
                embedding = None
            if embedding is not None:
                execution = await self._repository.search_hybrid(  # type: ignore[union-attr]
                    query_text=normalized,
                    query_embedding=embedding,
                    embedding_model=self._embedding_provider.model,
                    embedding_dimensions=self._embedding_provider.dimensions,
                    top_k=settings.RAG_TOP_K,
                    language=language,
                    lexical_candidate_limit=settings.RAG_LEXICAL_CANDIDATE_LIMIT,
                    semantic_candidate_limit=settings.RAG_SEMANTIC_CANDIDATE_LIMIT,
                    rrf_k=settings.RAG_RRF_K,
                )
                result = RetrievalResult(
                    chunks=execution.chunks,
                    query=normalized,
                    insufficient_evidence=not execution.chunks,
                    mode=execution.mode,
                    latency_ms=round((perf_counter() - started_at) * 1000),
                    candidate_count=execution.candidate_count,
                )
                return await self._record_trace(result, request_id=request_id)
        chunks = await self._repository.search(
            query_text=normalized,
            top_k=settings.RAG_TOP_K,
            language=language,
        )
        result = RetrievalResult(
            chunks=chunks,
            query=normalized,
            insufficient_evidence=not chunks,
            mode="lexical",
            latency_ms=round((perf_counter() - started_at) * 1000),
            candidate_count=len(chunks),
        )
        return await self._record_trace(result, request_id=request_id)

    async def _record_trace(self, result: RetrievalResult, *, request_id: str | None) -> RetrievalResult:
        recorder = getattr(self._repository, "record_retrieval_trace", None)
        if request_id and callable(recorder):
            try:
                await recorder(
                    request_id=request_id,
                    query_text=result.query,
                    retrieval_mode=result.mode,
                    retrieval_status="INSUFFICIENT_EVIDENCE" if result.insufficient_evidence else "COMPLETED",
                    candidate_count=result.candidate_count,
                    returned_chunk_ids=[chunk.chunk_id for chunk in result.chunks],
                    latency_ms=result.latency_ms,
                )
            except Exception:
                # Observability must not turn an evidence-backed answer into a failure.
                pass
        return result

    async def record_agent_execution(
        self,
        *,
        request_id: str,
        telemetry: AgentExecutionTelemetry,
        measurement_scope: str = "runtime_local",
    ) -> bool:
        """Persist aggregates only and report whether the write reached PostgreSQL."""
        recorder = getattr(self._repository, "record_agent_execution", None)
        if not request_id or not callable(recorder):
            return False
        try:
            await recorder(
                request_id=request_id,
                pipeline_version=settings.AGENT_PIPELINE_VERSION,
                model_name=settings.OPENAI_MODEL,
                execution_profile=configured_execution_profile(),
                measurement_scope=measurement_scope,
                terminal_status=telemetry.terminal_status,
                terminal_reason=telemetry.terminal_reason,
                model_turn_count=telemetry.model_turn_count,
                tool_call_count=telemetry.tool_call_count,
                tool_non_success_count=telemetry.tool_non_success_count,
                citation_count=telemetry.citation_count,
                answer_char_count=telemetry.answer_char_count,
                total_latency_ms=telemetry.total_latency_ms,
                model_latency_ms=telemetry.model_latency_ms,
                tool_latency_ms=telemetry.tool_latency_ms,
                tool_statuses=list(telemetry.tool_statuses),
            )
            return True
        except Exception as error:
            logger.warning(
                "agent_execution_metric_record_failed",
                extra={
                    "request_id": request_id[:80],
                    "error_type": type(error).__name__,
                },
            )
            return False

    async def close(self) -> None:
        if self._repository is not None:
            await self._repository.close()


# Retain the prior import name while PostgreSQL owns both search lanes.
HybridRetriever = PostgresRagRetriever
rag_retriever = PostgresRagRetriever()
