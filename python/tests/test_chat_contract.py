from __future__ import annotations

import asyncio
import importlib.util
from unittest.mock import AsyncMock, patch

from app.agent.contracts import AgentResult
from app.core.config import settings
from app.schemas.chat import AgentRunRequest, AgentRunResponse, ChatRequest, ChatResponse, FactPackage, StructuredAnswer
from app.services.ai_service import AIService


async def _collect_stream(service: AIService, request: ChatRequest) -> list[str]:
    return [event async for event in service.stream_chat_response(request)]


def test_chat_stream_returns_one_final_validated_response() -> None:
    service = AIService()
    expected = ChatResponse(reply="검증된 답변", status="grounded", agent_steps=["검증 통과"])
    service.process_chat = AsyncMock(return_value=expected)  # type: ignore[method-assign]

    events = asyncio.run(_collect_stream(service, ChatRequest(message="질문")))

    assert events == [f"event: done\ndata: {expected.model_dump_json()}\n\n"]


def test_chat_stream_returns_an_explicit_error_event() -> None:
    service = AIService()
    service.process_chat = AsyncMock(side_effect=RuntimeError("agent unavailable"))  # type: ignore[method-assign]

    events = asyncio.run(_collect_stream(service, ChatRequest(message="질문")))

    assert events == ['event: error\ndata: {"code": "chat_unavailable"}\n\n']


def test_chat_never_labels_an_uncited_completed_agent_answer_as_grounded() -> None:
    service = AIService()
    service.run_agent = AsyncMock(return_value=AgentRunResponse(
        requestId="chat-contract",
        status="completed",
        answer=StructuredAnswer(answer="근거 없는 답변", basisType="CURRENT_REPORT"),
        sources=[],
    ))  # type: ignore[method-assign]

    response = asyncio.run(service.process_chat(ChatRequest(message="질문")))

    assert response.status == "needs_context"
    assert response.agent_steps == ["인용 가능한 근거 출처가 없어 답변을 보류했습니다."]


def test_chat_facade_owns_only_the_grounded_agent_dependency() -> None:
    service = AIService()

    assert importlib.util.find_spec("app.services.knowledge_catalog") is not None
    assert importlib.util.find_spec("app.services.tools") is None
    assert importlib.util.find_spec("app.services.screen_tools") is None
    assert importlib.util.find_spec("app.services.field_guidance") is None
    assert hasattr(service, "_grounded_agent")
    assert not hasattr(service, "_field_guidance")
    assert not hasattr(service, "_local_chat")
    assert not hasattr(service, "_agent_graph")


def test_agent_failure_returns_a_safe_needs_context_response_without_a_legacy_fallback() -> None:
    service = AIService()
    service._grounded_agent.run = AsyncMock(return_value=AgentResult(  # type: ignore[method-assign]
        answer="AI 도우미를 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        status="failed",
        trace=["agent_error:ReadTimeout"],
    ))

    with patch.object(settings, "OPENAI_API_KEY", "test-key"):
        response = asyncio.run(service.run_agent(AgentRunRequest(
            fact_package=FactPackage(requestId="agent-failure", question="밭 상태를 알려줘")
        )))

    assert response.status == "needs_context"
    assert response.sources == []
    assert response.answer.answer == "현재 검증 가능한 답변을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
    assert response.trace == ["현재 AI 응답을 완료하지 못했습니다."]


def test_chat_report_context_is_converted_to_citable_authorized_facts() -> None:
    package = AIService._chat_request_to_fact_package(ChatRequest(
        message="현재 지역 위험을 설명해줘",
        context={
            "report": {
                "regionScore": 82,
                "regionGrade": "주의",
                "region": {"sidoName": "전북", "sigunguName": "익산시"},
                "recommendedCrops": [{"cropName": "상추", "score": 76}],
                "topRisks": [{"riskCode": "DROUGHT", "title": "가뭄 위험", "recommendedAction": "토양 수분을 확인하세요."}],
            }
        },
    ))

    assert package.facts["region.score"] == 82
    assert package.facts["crop.1.name"] == "상추"
    assert package.facts["risk.1.title"] == "가뭄 위험"
    assert package.sources == [{
        "sourceId": "farmflate:current-report",
        "provider": "Farmflate 현재 분석 리포트",
        "factKeyPrefixes": ["crop.", "region.", "risk."],
    }]
