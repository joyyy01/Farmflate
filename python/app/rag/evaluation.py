from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

from app.core.config import settings
from app.rag.models import RetrievalResult


@dataclass(frozen=True)
class EvaluationCase:
    case_id: str
    case_key: str
    query_text: str
    language: str
    expected_chunk_ids: tuple[str, ...]
    expected_citations: tuple[str, ...]
    evaluation_origin: str = "LEGACY"


@dataclass(frozen=True)
class EvaluationCorpusSnapshot:
    fingerprint: str
    document_count: int
    chunk_count: int
    evaluation_origin: str


@dataclass(frozen=True)
class EvaluationSummary:
    total: int
    passed: int
    failed: int
    errors: int
    run_id: str | None = None


class EvaluationRepository(Protocol):
    async def list_active_eval_cases(self, *, dataset_key: str, dataset_version: str) -> list[EvaluationCase]: ...

    async def record_eval_result(self, **arguments: object) -> None: ...


class RetrievalClient(Protocol):
    async def retrieve(self, query: str, *, language: str | None = None) -> RetrievalResult: ...


class RagEvaluator:
    """Deterministic retrieval evaluation; it does not use an LLM as a judge."""

    def __init__(self, repository: EvaluationRepository, retriever: RetrievalClient) -> None:
        self._repository = repository
        self._retriever = retriever

    async def evaluate_all(
        self,
        *,
        dataset_key: str = "legacy",
        dataset_version: str = "v1",
        evaluation_run_id: str | None = None,
    ) -> EvaluationSummary:
        cases = await self._repository.list_active_eval_cases(
            dataset_key=dataset_key,
            dataset_version=dataset_version,
        )
        passed = failed = errors = 0
        for case in cases:
            try:
                result = await self._retriever.retrieve(case.query_text, language=case.language)
                returned_chunk_ids = tuple(chunk.chunk_id for chunk in result.chunks)
                returned_citations = tuple(f"rag:{chunk_id}" for chunk_id in returned_chunk_ids)
                recall = _overlap_ratio(case.expected_chunk_ids, returned_chunk_ids)
                citation_precision = _overlap_ratio(returned_citations, case.expected_citations, denominator=returned_citations)
                first_relevant_rank = next(
                    (rank for rank, chunk_id in enumerate(returned_chunk_ids, start=1) if chunk_id in case.expected_chunk_ids),
                    None,
                )
                reciprocal_rank = round(1 / first_relevant_rank, 5) if first_relevant_rank else 0.0
                status = "PASSED" if first_relevant_rank else "FAILED"
                await self._repository.record_eval_result(
                    case_id=case.case_id,
                    evaluation_run_id=evaluation_run_id,
                    embedding_model=settings.RAG_EMBEDDING_MODEL if result.mode == "hybrid" else "postgres-fts",
                    retrieval_mode=result.mode,
                    result_status=status,
                    recall_at_k=recall,
                    citation_precision=citation_precision,
                    reciprocal_rank=reciprocal_rank,
                    first_relevant_rank=first_relevant_rank,
                    latency_ms=result.latency_ms,
                    evidence=[{"chunkId": chunk_id} for chunk_id in returned_chunk_ids],
                    retrieval_config={
                        "topK": settings.RAG_TOP_K,
                        "mode": result.mode,
                        "candidateCount": result.candidate_count,
                    },
                )
                if status == "PASSED":
                    passed += 1
                else:
                    failed += 1
            except Exception as error:
                errors += 1
                await self._repository.record_eval_result(
                    case_id=case.case_id,
                    evaluation_run_id=evaluation_run_id,
                    embedding_model="postgres-fts",
                    retrieval_mode="lexical",
                    result_status="ERROR",
                    recall_at_k=None,
                    citation_precision=None,
                    reciprocal_rank=None,
                    first_relevant_rank=None,
                    latency_ms=None,
                    evidence=[],
                    retrieval_config={"topK": settings.RAG_TOP_K},
                    failure_reason=f"{type(error).__name__}: {str(error)[:500]}",
                )
        return EvaluationSummary(total=len(cases), passed=passed, failed=failed, errors=errors, run_id=evaluation_run_id)


def _overlap_ratio(
    expected: Sequence[str],
    observed: Sequence[str],
    *,
    denominator: Sequence[str] | None = None,
) -> float | None:
    base = tuple(denominator) if denominator is not None else tuple(expected)
    if not base:
        return None
    return round(len(set(expected) & set(observed)) / len(set(base)), 5)
