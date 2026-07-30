from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path

from app.agent.contracts import AgentExecutionTelemetry, AgentResult
from app.agent.controlled_evaluation import ControlledAgentCase, ControlledAgentEvaluator, controlled_cases
from app.agent.execution_profile import configured_execution_profile
from app.agent.runner import GroundedAgent
from app.core.config import settings
from app.rag.retriever import PostgresRagRetriever
from app.schemas.chat import FactPackage


class _DryRunAgent:
    async def run(self, _: FactPackage) -> AgentResult:
        return AgentResult(
            answer="추가 현장 정보가 필요합니다.",
            status="needs_context",
            telemetry=AgentExecutionTelemetry(
                terminal_status="needs_context",
                terminal_reason="controlled_dry_run",
                model_turn_count=0,
                tool_call_count=0,
                tool_non_success_count=0,
                citation_count=0,
                answer_char_count=14,
                total_latency_ms=0,
                model_latency_ms=0,
                tool_latency_ms=0,
            ),
        )


async def _record_controlled_agent_execution(
    recorder: PostgresRagRetriever,
    case: ControlledAgentCase,
    result: AgentResult,
) -> None:
    """Fail the live evaluation when its aggregate evidence cannot be retained."""
    if result.telemetry is None:
        raise RuntimeError("controlled evaluation telemetry is missing")
    recorded = await recorder.record_agent_execution(
        request_id=case.fact_package.requestId,
        telemetry=result.telemetry,
        measurement_scope="controlled_local",
    )
    if not recorded:
        raise RuntimeError("controlled evaluation telemetry could not be persisted")


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a fixed, privacy-safe Farmflate Agent evaluation set.")
    parser.add_argument("--cases", type=int, default=30)
    execution_mode = parser.add_mutually_exclusive_group()
    execution_mode.add_argument("--live", action="store_true")
    execution_mode.add_argument("--dry-run", action="store_true")
    parser.add_argument("--concurrency", type=int, default=3)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


async def _run(*, cases: list[ControlledAgentCase], live: bool, concurrency: int = 1) -> dict[str, object]:
    if len(cases) < 30:
        raise ValueError("controlled evaluation requires at least 30 cases")
    if live:
        settings.validate_runtime()
        if not settings.OPENAI_API_KEY:
            raise RuntimeError("OPENAI_API_KEY must be configured for --live")
        agent = GroundedAgent()
    else:
        agent = _DryRunAgent()
    recorder = PostgresRagRetriever() if live else None

    async def record_case(case: ControlledAgentCase, result: AgentResult) -> None:
        if recorder is not None:
            await _record_controlled_agent_execution(recorder, case, result)

    try:
        summary = await ControlledAgentEvaluator(agent=agent).evaluate(
            cases=cases,
            execution_profile=configured_execution_profile(),
            max_concurrency=concurrency,
            after_case=record_case if recorder is not None else None,
        )
        return summary.to_dict()
    finally:
        if recorder is not None:
            await recorder.close()


def main() -> None:
    arguments = _arguments()
    if arguments.live and arguments.output is None:
        raise SystemExit("--live requires --output so the measurement evidence is retained.")
    result = asyncio.run(_run(
        cases=controlled_cases(arguments.cases),
        live=arguments.live,
        concurrency=arguments.concurrency,
    ))
    payload = json.dumps(result, ensure_ascii=False, indent=2)
    if arguments.output is not None:
        arguments.output.write_text(payload + "\n", encoding="utf-8")
    print(payload)


if __name__ == "__main__":
    main()
