from __future__ import annotations

import asyncio
from collections import Counter
from dataclasses import asdict, dataclass
from math import ceil
from typing import Awaitable, Callable, Protocol, Sequence

from app.agent.contracts import AgentResult
from app.schemas.chat import FactPackage


class ControlledAgent(Protocol):
    async def run(self, fact_package: FactPackage) -> AgentResult: ...


@dataclass(frozen=True)
class ControlledAgentCase:
    case_id: str
    fact_package: FactPackage


@dataclass(frozen=True)
class ControlledEvaluationSummary:
    sample_size: int
    completed_count: int
    needs_context_count: int
    failed_count: int
    structural_completion_rate: float
    p50_latency_ms: float | None
    p95_latency_ms: float | None
    execution_profile: str
    terminal_reason_counts: dict[str, int]
    max_concurrency: int
    measurement_scope: str = "controlled_local"

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


class ControlledAgentEvaluator:
    def __init__(self, *, agent: ControlledAgent) -> None:
        self._agent = agent

    async def evaluate(
            self,
            *,
            cases: Sequence[ControlledAgentCase],
            execution_profile: str,
            max_concurrency: int = 1,
            after_case: Callable[[ControlledAgentCase, AgentResult], Awaitable[None]] | None = None,
    ) -> ControlledEvaluationSummary:
        if max_concurrency < 1:
            raise ValueError("max_concurrency must be positive")
        semaphore = asyncio.Semaphore(max_concurrency)

        async def run_case(case: ControlledAgentCase) -> AgentResult:
            async with semaphore:
                result = await self._agent.run(case.fact_package)
                if after_case is not None:
                    await after_case(case, result)
                return result

        results = await asyncio.gather(*(run_case(case) for case in cases))
        latencies = sorted(
            float(result.telemetry.total_latency_ms)
            for result in results
            if result.telemetry is not None
        )
        completed_count = sum(result.status == "completed" for result in results)
        needs_context_count = sum(result.status == "needs_context" for result in results)
        failed_count = sum(result.status == "failed" for result in results)
        structurally_completed = sum(
            result.status == "completed" and bool(result.answer.strip()) and bool(result.citation_ids)
            for result in results
        )
        terminal_reason_counts = Counter(
            result.telemetry.terminal_reason if result.telemetry is not None else f"{result.status}:missing_telemetry"
            for result in results
        )
        sample_size = len(results)
        return ControlledEvaluationSummary(
            sample_size=sample_size,
            completed_count=completed_count,
            needs_context_count=needs_context_count,
            failed_count=failed_count,
            structural_completion_rate=round(structurally_completed / sample_size, 5) if sample_size else 0.0,
            p50_latency_ms=_percentile(latencies, 0.50),
            p95_latency_ms=_percentile(latencies, 0.95),
            execution_profile=execution_profile,
            terminal_reason_counts=dict(sorted(terminal_reason_counts.items())),
            max_concurrency=max_concurrency,
        )


def controlled_cases(case_count: int) -> list[ControlledAgentCase]:
    if case_count < 1:
        raise ValueError("case_count must be positive")
    questions = (
        "고온기에 토양 수분은 어떻게 점검해야 하나요?",
        "강우 뒤 배수 상태에서 우선 확인할 항목은 무엇인가요?",
        "습도가 높은 날 병해 징후를 어떻게 관찰해야 하나요?",
        "폭염 예보가 있을 때 관수 시점은 어떻게 정하나요?",
        "바람이 강한 날 방제 작업 전에 확인할 사항은 무엇인가요?",
        "일교차가 큰 시기에 작물 상태를 어떻게 관리하나요?",
        "집중호우 이후 포장에서 확인할 위험은 무엇인가요?",
        "토양 EC가 높을 때 추가로 필요한 정보는 무엇인가요?",
        "토양 pH 수치를 해석하려면 무엇을 함께 봐야 하나요?",
        "병해충 의심 증상이 있을 때 기록할 항목은 무엇인가요?",
        "정식 직후 관수 상태는 어떻게 확인하나요?",
        "가뭄이 이어질 때 토양 표면만 보고 판단하면 안 되는 이유는 무엇인가요?",
        "침수 뒤 뿌리 상태를 점검하는 순서는 무엇인가요?",
        "저온 예보가 있을 때 작물 피해를 줄이기 위한 점검은 무엇인가요?",
        "과습과 건조를 구분하려면 어떤 현장 정보가 필요한가요?",
        "웃자람이 의심될 때 확인할 재배 환경은 무엇인가요?",
        "수확 전 강우 예보가 있으면 어떤 기록을 확인해야 하나요?",
        "잎의 변색 원인을 판단하기 전에 확인할 사실은 무엇인가요?",
        "관수량 조정 전 토양 수분을 어떻게 측정해야 하나요?",
        "병해 확산 가능성을 판단할 때 기상 정보는 어떻게 사용하나요?",
        "방제 후 이상 증상이 지속되면 어떤 근거를 추가해야 하나요?",
        "일주일 작업 우선순위를 정할 때 필요한 정보는 무엇인가요?",
        "비료 투입 전 기존 토양 검사 결과에서 무엇을 봐야 하나요?",
        "작물 생육 부진을 진단하기 위한 최소 정보는 무엇인가요?",
        "고온과 높은 습도가 동시에 나타날 때 어떤 위험을 점검하나요?",
        "배수로 정비가 필요한지 판단하는 현장 징후는 무엇인가요?",
        "관찰 기록을 남길 때 사진 외에 어떤 수치를 함께 기록하나요?",
        "날씨 변화가 클 때 작업 계획을 언제 다시 검토해야 하나요?",
        "토양 상태와 기상 정보가 엇갈릴 때 무엇을 우선 확인하나요?",
        "근거가 부족할 때 에이전트에게 어떤 정보를 추가로 제공해야 하나요?",
    )
    return [
        ControlledAgentCase(
            case_id=f"controlled-{index + 1:02d}",
            fact_package=FactPackage(
                requestId=f"controlled-{index + 1:02d}",
                question=questions[index % len(questions)],
                userScope={"scope": "controlled_evaluation"},
                context={},
                facts={},
                sources=[],
            ),
        )
        for index in range(case_count)
    ]


def _percentile(values: list[float], quantile: float) -> float | None:
    if not values:
        return None
    return values[ceil(len(values) * quantile) - 1]
