from __future__ import annotations

import asyncio

from app.agent.contracts import AgentDraft, ToolCall
from app.agent.runner import GroundedAgent
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


def test_agent_returns_only_citations_supplied_by_its_executed_tools() -> None:
    package = FactPackage(
        requestId="request-1",
        question="내 밭 상태를 알려줘",
        facts={"field.status": "CAUTION"},
        sources=[
            {"sourceId": "field-report-1", "provider": "Farmflate"},
            {"sourceId": "field-report-2", "provider": "Farmflate"},
        ],
    )

    result = asyncio.run(GroundedAgent(model=ContextThenAnswerModel()).run(package))

    assert result.status == "completed"
    assert result.citation_ids == ["fact:field-report-1"]
    assert result.answer == "현재 밭 상태는 주의입니다."


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
            {"sourceId": "field-report-1", "provider": "Farmflate"},
            {"sourceId": "field-report-2", "provider": "Farmflate"},
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
