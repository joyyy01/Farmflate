from __future__ import annotations

import asyncio

from app.rag.evaluation import EvaluationCase, RagEvaluator
from app.rag.models import RetrievedChunk, RetrievalResult


class RecordingEvaluationRepository:
    def __init__(self) -> None:
        self.results: list[dict[str, object]] = []

    async def list_active_eval_cases(self, *, dataset_key: str, dataset_version: str) -> list[EvaluationCase]:
        assert (dataset_key, dataset_version) == ("agri-guidance", "2026-07")
        return [EvaluationCase(
            case_id="case-1",
            case_key="soil-drainage",
            query_text="토양 배수 관리",
            language="ko",
            expected_chunk_ids=("chunk-1", "chunk-2"),
            expected_citations=("rag:chunk-1",),
        )]

    async def record_eval_result(self, **arguments: object) -> None:
        self.results.append(arguments)


class DeterministicRetriever:
    async def retrieve(self, query: str, *, language: str | None = None) -> RetrievalResult:
        assert query == "토양 배수 관리"
        assert language == "ko"
        return RetrievalResult(
            chunks=[RetrievedChunk(
                chunk_id="chunk-1",
                document_id="document-1",
                source_id="source-1",
                source_name="농사로",
                canonical_url="https://example.go.kr/soil",
                title="토양 배수 관리",
                content="배수 상태를 확인하세요.",
                score=0.8,
                metadata={},
                retrieval_paths=("lexical", "semantic"),
            )],
            query=query,
            insufficient_evidence=False,
            mode="hybrid",
            latency_ms=12,
            candidate_count=2,
        )


def test_evaluator_persists_recall_citation_precision_and_retrieval_mode() -> None:
    repository = RecordingEvaluationRepository()

    summary = asyncio.run(RagEvaluator(repository, DeterministicRetriever()).evaluate_all(
        dataset_key="agri-guidance",
        dataset_version="2026-07",
        evaluation_run_id="run-1",
    ))  # type: ignore[arg-type]

    assert summary.total == 1
    assert summary.passed == 1
    assert summary.failed == 0
    assert repository.results[0]["recall_at_k"] == 0.5
    assert repository.results[0]["citation_precision"] == 1.0
    assert repository.results[0]["reciprocal_rank"] == 1.0
    assert repository.results[0]["first_relevant_rank"] == 1
    assert repository.results[0]["retrieval_mode"] == "hybrid"
    assert repository.results[0]["evaluation_run_id"] == "run-1"
