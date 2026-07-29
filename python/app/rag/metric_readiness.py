from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class MetricReadiness:
    eligible: bool
    claim_scope: str
    reasons: tuple[str, ...]


def assess_metric_readiness(
        *,
        sample_size: int,
        measurement_scope: str,
        failed_count: int,
        minimum_sample_size: int = 30,
) -> MetricReadiness:
    del failed_count  # Failures remain visible in the report; they do not disappear from the denominator.
    reasons: list[str] = []
    if sample_size < minimum_sample_size:
        reasons.append("minimum_sample_size_not_met")
    if measurement_scope != "controlled_local":
        reasons.append("unsupported_measurement_scope")
    return MetricReadiness(
        eligible=not reasons,
        claim_scope=measurement_scope,
        reasons=tuple(reasons),
    )
