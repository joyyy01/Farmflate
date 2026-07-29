from __future__ import annotations

import asyncio
from unittest.mock import patch

import pytest

from app.agent.contracts import AgentDraft, ToolCall, ToolCitation, ToolResult
from app.agent.responses_client import ResponseContractError
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


class FailingModel:
    async def next_turn(self, **_: object) -> AgentDraft:
        raise TimeoutError("upstream response timed out")


class ContractViolatingModel:
    async def next_turn(self, **_: object) -> AgentDraft:
        raise ResponseContractError("model output did not satisfy the response contract")


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
    assert result.telemetry is not None
    assert result.telemetry.model_turn_count == 2
    assert result.telemetry.tool_call_count == 1
    assert result.telemetry.tool_non_success_count == 0
    assert result.telemetry.citation_count == 1
    assert result.telemetry.terminal_status == "completed"
    assert result.telemetry.terminal_reason == "completed"


def test_agent_records_the_failure_type_in_terminal_telemetry() -> None:
    result = asyncio.run(GroundedAgent(model=FailingModel()).run(
        FactPackage(requestId="request-timeout", question="현재 상태를 알려줘"),
    ))

    assert result.status == "failed"
    assert result.telemetry is not None
    assert result.telemetry.terminal_reason == "agent_error:TimeoutError"


def test_agent_turns_a_model_contract_violation_into_safe_needs_context() -> None:
    result = asyncio.run(GroundedAgent(model=ContractViolatingModel()).run(
        FactPackage(requestId="request-contract", question="현재 상태를 알려줘"),
    ))

    assert result.status == "needs_context"
    assert result.telemetry is not None
    assert result.telemetry.terminal_reason == "model_contract_violation"


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


def test_remote_agent_presentation_does_not_append_uncited_screen_facts() -> None:
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

    draft = AgentDraft(
        answer="상추 재배에서는 가뭄 위험을 우선 확인해야 합니다.",
        claims=[{
            "text": "상추 재배에서는 가뭄 위험을 우선 확인해야 합니다.",
            "citation_ids": ["fact:report-1"],
        }],
        citation_ids=["fact:report-1"],
        status="completed",
    )
    result = GroundedAgent._validated_draft(
        draft,
        {"fact:report-1": ToolCitation("fact:report-1", "분석 리포트")},
        [],
    )

    assert result.status == "completed"
    assert result.answer == draft.answer


def test_agent_refuses_a_completed_draft_when_its_claim_is_not_rendered_in_the_answer() -> None:
    draft = AgentDraft(
        answer="검증된 첫 문장입니다.",
        claims=[{
            "text": "답변에 없는 주장입니다.",
            "citation_ids": ["rag:chunk-1"],
        }],
        citation_ids=["rag:chunk-1"],
        status="completed",
    )

    result = GroundedAgent._validated_draft(
        draft,
        {"rag:chunk-1": ToolCitation("rag:chunk-1", "공식 농업 자료")},
        [],
    )

    assert result.status == "needs_context"
    assert result.trace[-1] == "citation_validation_failed"
