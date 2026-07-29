import asyncio
from unittest.mock import patch

from app.agent.contracts import AgentExecutionTelemetry, AgentResult, ToolCitation
from app.core.config import settings
from app.schemas.chat import AgentRunRequest, FactPackage
from app.services.ai_service import AIService, aggregate_grounding_sources


def test_grounding_sources_aggregate_multiple_chunk_citations_from_the_same_document() -> None:
    sources = aggregate_grounding_sources([
        ToolCitation("rag:chunk-1", "주간 농사정보", "https://example.test/weekly"),
        ToolCitation("rag:chunk-2", "주간 농사정보", "https://example.test/weekly"),
        ToolCitation("rag:chunk-3", "병해충 방제", "https://example.test/pest"),
    ])

    assert len(sources) == 2
    assert sources[0]["title"] == "주간 농사정보"
    assert sources[0]["evidenceCount"] == 2
    assert sources[0]["citationIds"] == ["rag:chunk-1", "rag:chunk-2"]


def test_agent_facade_records_aggregate_telemetry_without_exposing_it_to_chat_response() -> None:
    telemetry = AgentExecutionTelemetry(
        terminal_status="completed",
        terminal_reason="completed",
        model_turn_count=2,
        tool_call_count=1,
        tool_non_success_count=0,
        citation_count=0,
        answer_char_count=12,
        total_latency_ms=210,
        model_latency_ms=180,
        tool_latency_ms=30,
        tool_statuses=("ok",),
    )

    class StubAgent:
        async def run(self, _: FactPackage) -> AgentResult:
            return AgentResult(answer="검증된 답변입니다.", status="completed", telemetry=telemetry)

    class RecordingRetriever:
        def __init__(self) -> None:
            self.arguments: dict[str, object] | None = None

        async def record_agent_execution(self, **arguments: object) -> None:
            self.arguments = arguments

    service = AIService()
    service._grounded_agent = StubAgent()  # type: ignore[assignment]
    retriever = RecordingRetriever()
    request = AgentRunRequest(fact_package=FactPackage(requestId="metric-request", question="밭 상태를 알려줘"))

    with (
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
        patch("app.services.ai_service.rag_retriever", retriever),
    ):
        response = asyncio.run(service.run_agent(request))

    assert response.status == "completed"
    assert retriever.arguments == {
        "request_id": "metric-request",
        "telemetry": telemetry,
        "measurement_scope": "runtime_local",
    }
    assert "telemetry" not in response.model_dump()
