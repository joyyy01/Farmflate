from __future__ import annotations

import argparse
import asyncio
import json

from app.core.config import settings
from app.agent.execution_profile import configured_execution_profile
from app.rag.metric_readiness import assess_metric_readiness
from app.rag.repository import RagRepository


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Summarize PostgreSQL RAG and Agent quality/latency metrics.")
    parser.add_argument("--days", type=int, default=30)
    return parser.parse_args()


async def _run(days: int) -> dict[str, object]:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for metrics.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        retrieval, agent = await asyncio.gather(
            repository.retrieval_quality_metrics(days=days),
            repository.agent_execution_metrics(
                days=days,
                pipeline_version=settings.AGENT_PIPELINE_VERSION,
                model_name=settings.OPENAI_MODEL,
                execution_profile=configured_execution_profile(),
                measurement_scope="controlled_local",
            ),
        )
        readiness = assess_metric_readiness(
            sample_size=agent.request_count,
            measurement_scope=agent.measurement_scope,
            failed_count=agent.failed_count,
        )
        return {
            "windowDays": days,
            "retrieval": [metric.__dict__ for metric in retrieval],
            "agent": agent.__dict__,
            "metricReadiness": readiness.__dict__,
        }
    finally:
        await repository.close()


def main() -> None:
    print(json.dumps(asyncio.run(_run(_arguments().days)), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
