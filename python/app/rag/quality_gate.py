from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class EvaluationRunMetric:
    run_id: str
    dataset_key: str
    dataset_version: str
    retrieval_mode: str
    case_count: int
    completed_count: int
    error_count: int
    avg_recall_at_k: float | None
    avg_citation_precision: float | None
    avg_reciprocal_rank: float | None
    p95_latency_ms: float | None
    corpus_fingerprint: str
    evaluation_origin: str


@dataclass(frozen=True)
class QualityGatePolicy:
    min_cases: int
    min_recall_delta: float
    max_citation_precision_drop: float
    max_p95_latency_ratio: float


@dataclass(frozen=True)
class QualityGateDecision:
    status: str
    reasons: tuple[str, ...]
    recall_delta: float | None
    citation_precision_delta: float | None
    p95_latency_ratio: float | None


def compare_runs(
    baseline: EvaluationRunMetric,
    candidate: EvaluationRunMetric,
    policy: QualityGatePolicy,
) -> QualityGateDecision:
    """Return a review decision; this function never turns a runtime flag on."""
    reasons: list[str] = []
    if (baseline.dataset_key, baseline.dataset_version) != (candidate.dataset_key, candidate.dataset_version):
        reasons.append("평가 세트 또는 버전이 달라 비교할 수 없습니다.")
    if baseline.corpus_fingerprint != candidate.corpus_fingerprint:
        reasons.append("코퍼스 지문이 달라 비교할 수 없습니다.")
    if baseline.evaluation_origin != candidate.evaluation_origin:
        reasons.append("평가 라벨 origin이 달라 비교할 수 없습니다.")
    if baseline.case_count < policy.min_cases or candidate.case_count < policy.min_cases:
        reasons.append(f"승격 판단에는 동일 평가 세트의 사례가 최소 {policy.min_cases}건 필요합니다.")
    if baseline.completed_count != baseline.case_count or candidate.completed_count != candidate.case_count:
        reasons.append("완료되지 않은 평가 사례가 있어 품질을 비교할 수 없습니다.")
    if baseline.error_count or candidate.error_count:
        reasons.append("평가 실행 오류가 있어 자동 승격을 판단할 수 없습니다.")
    values = (
        baseline.avg_recall_at_k, candidate.avg_recall_at_k,
        baseline.avg_citation_precision, candidate.avg_citation_precision,
        baseline.avg_reciprocal_rank, candidate.avg_reciprocal_rank,
        baseline.p95_latency_ms, candidate.p95_latency_ms,
    )
    if any(value is None for value in values):
        reasons.append("recall, precision@k, MRR, p95 지연시간이 모두 측정돼야 합니다.")

    if reasons:
        return QualityGateDecision("NOT_READY", tuple(reasons), None, None, None)

    recall_delta = round(candidate.avg_recall_at_k - baseline.avg_recall_at_k, 5)  # type: ignore[operator]
    citation_delta = round(candidate.avg_citation_precision - baseline.avg_citation_precision, 5)  # type: ignore[operator]
    p95_ratio = round(candidate.p95_latency_ms / max(baseline.p95_latency_ms, 1.0), 5)  # type: ignore[arg-type]
    if recall_delta < policy.min_recall_delta:
        reasons.append(f"recall@k 개선폭이 기준 {policy.min_recall_delta:.2f}에 미달합니다.")
    if citation_delta < -policy.max_citation_precision_drop:
        reasons.append("인용 정확도가 기준보다 낮아졌습니다.")
    if p95_ratio > policy.max_p95_latency_ratio:
        reasons.append("p95 지연시간 상한을 초과했습니다.")
    return QualityGateDecision(
        "MANUAL_REVIEW" if not reasons else "NOT_READY",
        tuple(reasons),
        recall_delta,
        citation_delta,
        p95_ratio,
    )
