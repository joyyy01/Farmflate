from __future__ import annotations

import sys
from unittest.mock import patch

import asyncio

import pytest

from app.agent.contracts import AgentExecutionTelemetry, AgentResult
from app.agent.controlled_evaluation import ControlledAgentCase
from app.agent.controlled_evaluation_cli import _arguments, _record_controlled_agent_execution
from app.schemas.chat import FactPackage


def test_cli_accepts_explicit_dry_run_mode() -> None:
    with patch.object(sys, "argv", ["controlled_evaluation_cli.py", "--dry-run", "--cases", "30"]):
        arguments = _arguments()

    assert arguments.live is False
    assert arguments.dry_run is True


def test_live_evaluation_rejects_an_unpersisted_agent_metric() -> None:
    class UnavailableRecorder:
        async def record_agent_execution(self, **_: object) -> bool:
            return False

    case = ControlledAgentCase(
        case_id="controlled-01",
        fact_package=FactPackage(requestId="controlled-01", question="고온기 점검 항목은 무엇인가요?"),
    )
    result = AgentResult(
        answer="추가 정보가 필요합니다.",
        status="needs_context",
        telemetry=AgentExecutionTelemetry(
            terminal_status="needs_context",
            terminal_reason="model_needs_context",
            model_turn_count=1,
            tool_call_count=0,
            tool_non_success_count=0,
            citation_count=0,
            answer_char_count=14,
            total_latency_ms=120,
            model_latency_ms=120,
            tool_latency_ms=0,
        ),
    )

    with pytest.raises(RuntimeError, match="telemetry could not be persisted"):
        asyncio.run(_record_controlled_agent_execution(UnavailableRecorder(), case, result))
