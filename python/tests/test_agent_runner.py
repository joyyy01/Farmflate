from __future__ import annotations

import asyncio
from unittest.mock import patch

import pytest

from app.agent.contracts import AgentDraft, ToolCall, ToolResult
from app.agent.runner import GroundedAgent
from app.core.config import settings
from app.schemas.chat import FactPackage


class ContextThenAnswerModel:
    def __init__(self) -> None:
        self.turn = 0

    async def next_turn(self, **_: object) -> ToolCall | AgentDraft:
        self.turn += 1
        if self.turn == 1:
            return ToolCall(call_id="call-1", name="read_authorized_context", arguments={"section": "field"})
        return AgentDraft(
            answer="현재 밭 상태는 주의입니다.",
            claims=[{"text": "현재 밭 상태는 주의입니다.", "citation_ids": ["fact:field-report-1"]}],
            citation_ids=["fact:field-report-1"],
            status="completed",
        )


class ComponentClimateThenAnswerModel:
    def __init__(self) -> None:
        self.turn = 0

    async def next_turn(self, **_: object) -> ToolCall | AgentDraft:
        self.turn += 1
        if self.turn == 1:
            return ToolCall(call_id="call-1", name="read_authorized_context", arguments={"section": "climate"})
        return AgentDraft(
            answer="저장된 지역 분석에서 기후 점수는 87점입니다.",
            claims=[{"text": "기후 점수는 87점입니다.", "citation_ids": ["fact:region-report"]}],
            citation_ids=["fact:region-report"],
            status="completed",
        )


class UnknownCitationModel:
    async def next_turn(self, **_: object) -> AgentDraft:
        return AgentDraft(
            answer="확인되지 않은 답변입니다.",
            claims=[{"text": "확인되지 않은 답변입니다.", "citation_ids": ["rag:not-returned"]}],
            citation_ids=["rag:not-returned"],
            status="completed",
        )


class ClaimCitationMismatchModel:
    def __init__(self) -> None:
        self.turn = 0

    async def next_turn(self, **_: object) -> ToolCall | AgentDraft:
        self.turn += 1
        if self.turn == 1:
            return ToolCall(call_id="call-1", name="read_authorized_context", arguments={"section": "field"})
        return AgentDraft(
            answer="검증된 것처럼 보이는 답변입니다.",
            claims=[{"text": "검증된 것처럼 보이는 답변입니다.", "citation_ids": ["fact:field-report-1"]}],
            citation_ids=["fact:field-report-2"],
            status="completed",
        )


class HistoryCapturingModel:
    def __init__(self) -> None:
        self.history: object | None = None

    async def next_turn(self, **kwargs: object) -> AgentDraft:
        self.history = kwargs.get("history")
        return AgentDraft(answer="추가 정보가 필요합니다.", claims=[], citation_ids=[], status="needs_context")


class RepeatingToolCallModel:
    async def next_turn(self, **_: object) -> ToolCall:
        return ToolCall(call_id="call", name="read_authorized_context", arguments={"section": "all"})


class RecordingToolExecutor:
    def __init__(self) -> None:
        self.calls = 0

    async def execute(self, **_: object) -> ToolResult:
        self.calls += 1
        return ToolResult(status="ok")


def test_agent_returns_only_citations_supplied_by_its_executed_tools() -> None:
    package = FactPackage(
        requestId="request-1",
        question="내 밭 상태를 알려줘",
        facts={"field.status": "CAUTION"},
        sources=[
            {"sourceId": "field-report-1", "provider": "Farmflate", "factKeyPrefixes": ["field."]},
            {"sourceId": "field-report-2", "provider": "Farmflate", "factKeyPrefixes": ["field."]},
        ],
    )

    result = asyncio.run(GroundedAgent(model=ContextThenAnswerModel()).run(package))

    assert result.status == "completed"
    assert result.citation_ids == ["fact:field-report-1"]
    assert result.answer == "현재 밭 상태는 주의입니다."


def test_agent_completes_with_component_climate_facts_and_their_provenance_source() -> None:
    package = FactPackage(
        requestId="request-component-climate",
        question="지역 기후 점수 근거를 알려줘",
        facts={"component.climate.score": 87},
        sources=[
            {"sourceId": "region-report", "provider": "Farmflate", "factKeyPrefixes": ["component.climate."]},
        ],
    )

    result = asyncio.run(GroundedAgent(model=ComponentClimateThenAnswerModel()).run(package))

    assert result.status == "completed"
    assert result.citation_ids == ["fact:region-report"]


def test_agent_refuses_a_completed_answer_with_a_citation_no_tool_returned() -> None:
    result = asyncio.run(
        GroundedAgent(model=UnknownCitationModel()).run(
            FactPackage(requestId="request-2", question="근거 없는 답변을 해줘")
        )
    )

    assert result.status == "needs_context"
    assert result.citation_ids == []


def test_agent_rejects_when_claim_citations_are_not_exposed_in_the_response_contract() -> None:
    package = FactPackage(
        requestId="request-3",
        question="내 밭 상태를 알려줘",
        facts={"field.status": "CAUTION"},
        sources=[
            {"sourceId": "field-report-1", "provider": "Farmflate", "factKeyPrefixes": ["field."]},
            {"sourceId": "field-report-2", "provider": "Farmflate", "factKeyPrefixes": ["field."]},
        ],
    )

    result = asyncio.run(GroundedAgent(model=ClaimCitationMismatchModel()).run(package))

    assert result.status == "needs_context"


def test_agent_passes_sanitized_conversation_context_to_the_model() -> None:
    model = HistoryCapturingModel()
    package = FactPackage(
        requestId="request-4",
        question="그럼 오늘은요?",
        history=[{"role": "user", "content": "어제 밭이 건조하다고 했어요."}],
    )

    result = asyncio.run(GroundedAgent(model=model).run(package))

    assert result.status == "needs_context"
    assert model.history == package.history


def test_agent_caps_tool_steps_at_two_before_stopping() -> None:
    tools = RecordingToolExecutor()
    result = asyncio.run(
        GroundedAgent(model=RepeatingToolCallModel(), tools=tools).run(
            FactPackage(requestId="request-5", question="근거를 찾아줘")
        )
    )

    assert result.status == "needs_context"
    assert tools.calls == 2
    assert result.trace[-1] == "tool_limit"


def test_runtime_rejects_any_tool_step_limit_other_than_two() -> None:
    with (
        patch.object(settings, "INTERNAL_API_KEY", "internal-test-key"),
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag:secret@db/rag"),
        patch.object(settings, "AGENT_MAX_TOOL_CALLS", 3),
    ):
        with pytest.raises(RuntimeError, match="AGENT_MAX_TOOL_CALLS"):
            settings.validate_runtime()


def test_remote_agent_presentation_expands_short_grounded_answers_in_korean() -> None:
    package = FactPackage(
        requestId="presentation",
        question="상추 재배의 가뭄 위험을 알려줘",
        facts={
            "region.name": "전북 익산시",
            "crop.1.name": "상추",
            "risk.1.title": "가뭄 위험",
            "risk.1.action.1": "토양 수분을 확인하고 관수 일정을 조정하세요.",
        },
    )

    answer = GroundedAgent._present_remote_answer("상추 재배에서는 가뭄 위험을 우선 확인해야 합니다.", package)

    assert len(answer) >= 240
    assert all(heading in answer for heading in ("핵심 판단", "근거", "지금 할 일"))
    assert "토양 수분을 확인하고 관수 일정을 조정하세요." in answer
