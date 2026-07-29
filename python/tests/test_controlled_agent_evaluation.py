from __future__ import annotations

import asyncio

from app.agent.contracts import AgentExecutionTelemetry, AgentResult
from app.agent.controlled_evaluation import ControlledAgentCase, ControlledAgentEvaluator
from app.schemas.chat import FactPackage


class _FakeAgent:
    def __init__(self) -> None:
        self._results = [
            AgentResult(
                answer="근거 기반 안내",
                status="completed",
                citation_ids=["rag:chunk-1"],
                telemetry=_telemetry("completed", 100),
            ),
            AgentResult(
                answer="추가 정보가 필요합니다.",
                status="needs_context",
                telemetry=_telemetry("needs_context", 120),
            ),
            AgentResult(
                answer="",
                status="failed",
                telemetry=_telemetry("failed", 140),
            ),
        ]

    async def run(self, _: FactPackage) -> AgentResult:
        return self._results.pop(0)


def _telemetry(status: str, latency_ms: int) -> AgentExecutionTelemetry:
    return AgentExecutionTelemetry(
        terminal_status=status,  # type: ignore[arg-type]
        terminal_reason=status,
        model_turn_count=1,
        tool_call_count=0,
        tool_non_success_count=0,
        citation_count=0,
        answer_char_count=0,
        total_latency_ms=latency_ms,
        model_latency_ms=latency_ms,
        tool_latency_ms=0,
    )


def test_controlled_evaluation_counts_terminal_results_without_network() -> None:
    evaluator = ControlledAgentEvaluator(agent=_FakeAgent())
    cases = [
        ControlledAgentCase(case_id=f"case-{index}", fact_package=FactPackage(requestId=f"case-{index}", question="현재 상태를 알려줘"))
        for index in range(3)
    ]

    summary = asyncio.run(evaluator.evaluate(cases=cases, execution_profile="agent-test-profile"))

    assert summary.sample_size == 3
    assert summary.completed_count == 1
    assert summary.needs_context_count == 1
    assert summary.failed_count == 1
    assert summary.measurement_scope == "controlled_local"
    assert summary.max_concurrency == 1
    assert summary.p95_latency_ms == 140.0
    assert summary.terminal_reason_counts == {
        "completed": 1,
        "failed": 1,
        "needs_context": 1,
    }


def test_controlled_evaluation_can_record_each_case_without_changing_results() -> None:
    class RecordingAgent:
        async def run(self, _: FactPackage) -> AgentResult:
            return AgentResult(
                answer="근거 기반 안내",
                status="completed",
                citation_ids=["rag:chunk-1"],
                telemetry=_telemetry("completed", 100),
            )

    recorded: list[str] = []

    async def record(case: ControlledAgentCase, _: AgentResult) -> None:
        recorded.append(case.case_id)

    summary = asyncio.run(ControlledAgentEvaluator(agent=RecordingAgent()).evaluate(
        cases=[ControlledAgentCase(case_id="case-1", fact_package=FactPackage(requestId="case-1", question="상태를 알려줘"))],
        execution_profile="agent-test-profile",
        after_case=record,
    ))

    assert summary.completed_count == 1
    assert recorded == ["case-1"]
