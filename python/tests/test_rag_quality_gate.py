from app.rag.quality_gate import EvaluationRunMetric, QualityGatePolicy, compare_runs


def _metric(*, run_id: str, mode: str, recall: float, citation_precision: float, p95: float) -> EvaluationRunMetric:
    return EvaluationRunMetric(
        run_id=run_id,
        dataset_key="agri-guidance",
        dataset_version="2026-07",
        retrieval_mode=mode,
        case_count=20,
        completed_count=20,
        error_count=0,
        avg_recall_at_k=recall,
        avg_citation_precision=citation_precision,
        avg_reciprocal_rank=recall,
        p95_latency_ms=p95,
        corpus_fingerprint="a" * 64,
        evaluation_origin="AUTO_GENERATED",
    )


def test_quality_gate_requires_a_reproducible_improvement_before_manual_hybrid_review() -> None:
    decision = compare_runs(
        _metric(run_id="lexical", mode="lexical", recall=0.70, citation_precision=0.90, p95=100),
        _metric(run_id="hybrid", mode="hybrid", recall=0.77, citation_precision=0.90, p95=140),
        QualityGatePolicy(20, 0.05, 0.0, 1.5),
    )

    assert decision.status == "MANUAL_REVIEW"
    assert decision.recall_delta == 0.07
    assert decision.p95_latency_ratio == 1.4


def test_quality_gate_refuses_to_promote_a_small_or_mismatched_evaluation_set() -> None:
    baseline = _metric(run_id="lexical", mode="lexical", recall=0.70, citation_precision=0.90, p95=100)
    candidate = _metric(run_id="hybrid", mode="hybrid", recall=0.90, citation_precision=0.90, p95=120)
    candidate = EvaluationRunMetric(**{**candidate.__dict__, "dataset_version": "2026-08", "case_count": 2})

    decision = compare_runs(baseline, candidate, QualityGatePolicy(20, 0.05, 0.0, 1.5))

    assert decision.status == "NOT_READY"
    assert any("버전" in reason for reason in decision.reasons)
    assert any("최소 20건" in reason for reason in decision.reasons)


def test_quality_gate_refuses_to_compare_runs_from_different_corpus_snapshots() -> None:
    baseline = _metric(run_id="lexical", mode="lexical", recall=0.70, citation_precision=0.90, p95=100)
    candidate = EvaluationRunMetric(**{**_metric(
        run_id="hybrid", mode="hybrid", recall=0.80, citation_precision=0.90, p95=110,
    ).__dict__, "corpus_fingerprint": "b" * 64})

    decision = compare_runs(baseline, candidate, QualityGatePolicy(20, 0.05, 0.0, 1.5))

    assert decision.status == "NOT_READY"
    assert any("코퍼스" in reason for reason in decision.reasons)
