from __future__ import annotations

import argparse
import asyncio
import json
from dataclasses import asdict

from app.core.config import settings
from app.rag.quality_gate import QualityGatePolicy, compare_runs
from app.rag.repository import RagRepository


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare two fixed RAG evaluation runs without changing runtime flags.")
    parser.add_argument("--baseline-run", required=True)
    parser.add_argument("--candidate-run", required=True)
    return parser.parse_args()


async def _run(args: argparse.Namespace) -> dict[str, object]:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for evaluation comparison.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        baseline, candidate = await asyncio.gather(
            repository.evaluation_run_metrics(run_id=args.baseline_run),
            repository.evaluation_run_metrics(run_id=args.candidate_run),
        )
        decision = compare_runs(
            baseline,
            candidate,
            QualityGatePolicy(
                min_cases=settings.RAG_EVALUATION_MIN_CASES,
                min_recall_delta=settings.RAG_HYBRID_MIN_RECALL_DELTA,
                max_citation_precision_drop=settings.RAG_HYBRID_MAX_CITATION_PRECISION_DROP,
                max_p95_latency_ratio=settings.RAG_HYBRID_MAX_P95_LATENCY_RATIO,
            ),
        )
        return {"baseline": asdict(baseline), "candidate": asdict(candidate), "decision": asdict(decision)}
    finally:
        await repository.close()


def main() -> None:
    print(json.dumps(asyncio.run(_run(_arguments())), ensure_ascii=False))


if __name__ == "__main__":
    main()
