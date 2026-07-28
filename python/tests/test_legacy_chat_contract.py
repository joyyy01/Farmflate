from __future__ import annotations

import asyncio
import importlib.util
from unittest.mock import AsyncMock

from app.schemas.chat import AgentRunResponse, ChatRequest, ChatResponse, StructuredAnswer
from app.services.ai_service import AIService


async def _collect_stream(service: AIService, request: ChatRequest) -> list[str]:
    return [event async for event in service.stream_chat_response(request)]


def test_legacy_chat_stream_returns_one_final_validated_response() -> None:
    service = AIService()
    expected = ChatResponse(reply="검증된 답변", status="grounded", agent_steps=["검증 통과"])
    service.process_chat = AsyncMock(return_value=expected)  # type: ignore[method-assign]

    events = asyncio.run(_collect_stream(service, ChatRequest(message="질문")))

    assert events == [f"event: done\ndata: {expected.model_dump_json()}\n\n"]


def test_legacy_chat_stream_returns_an_explicit_error_event() -> None:
    service = AIService()
    service.process_chat = AsyncMock(side_effect=RuntimeError("agent unavailable"))  # type: ignore[method-assign]

    events = asyncio.run(_collect_stream(service, ChatRequest(message="질문")))

    assert events == ['event: error\ndata: {"code": "chat_unavailable"}\n\n']


def test_legacy_chat_never_labels_an_uncited_completed_agent_answer_as_grounded() -> None:
    service = AIService()
    service.run_agent = AsyncMock(return_value=AgentRunResponse(
        requestId="legacy-chat",
        status="completed",
        answer=StructuredAnswer(answer="근거 없는 답변", basisType="CURRENT_REPORT"),
        sources=[],
    ))  # type: ignore[method-assign]

    response = asyncio.run(service.process_chat(ChatRequest(message="질문")))

    assert response.status == "needs_context"
    assert "인용 가능한 근거 출처가 없습니다." in response.agent_steps


def test_legacy_chat_dependencies_have_specific_names_and_the_facade_does_not_own_the_graph() -> None:
    service = AIService()

    assert importlib.util.find_spec("app.services.knowledge_catalog") is not None
    assert importlib.util.find_spec("app.services.screen_context") is not None
    assert importlib.util.find_spec("app.services.tools") is None
    assert importlib.util.find_spec("app.services.screen_tools") is None
    assert hasattr(service, "_legacy_chat")
    assert not hasattr(service, "_agent_graph")
