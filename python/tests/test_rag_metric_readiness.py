from __future__ import annotations

from app.rag.metric_readiness import assess_metric_readiness


def test_metrics_are_not_quote_eligible_before_thirty_same_profile_samples() -> None:
    readiness = assess_metric_readiness(
        sample_size=29,
        measurement_scope="controlled_local",
        failed_count=0,
    )

    assert readiness.eligible is False
    assert readiness.reasons == ("minimum_sample_size_not_met",)


def test_metrics_are_quote_eligible_only_as_controlled_local_evidence() -> None:
    readiness = assess_metric_readiness(
        sample_size=30,
        measurement_scope="controlled_local",
        failed_count=1,
    )

    assert readiness.eligible is True
    assert readiness.claim_scope == "controlled_local"
