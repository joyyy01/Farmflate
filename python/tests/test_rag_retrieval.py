from __future__ import annotations

import asyncio
from unittest.mock import patch

from app.agent.contracts import AgentExecutionTelemetry
from app.agent.tools import AgentToolExecutor
from app.core.config import settings
from app.rag.models import RetrievedChunk
from app.rag.repository import RetrievalExecution
from app.rag.retriever import HybridRetriever
from app.schemas.chat import FactPackage


class RecordingRepository:
    def __init__(self) -> None:
        self.arguments: dict[str, object] | None = None

    async def search(self, **arguments: object) -> list[RetrievedChunk]:
        self.arguments = arguments
        return [RetrievedChunk(
            chunk_id="chunk-1", document_id="document-1", source_id="source-1",
            source_name="농사로", canonical_url="https://example.go.kr/soil",
            title="토양 관리", content="토양 상태를 확인하세요.", score=0.04, metadata={},
        )]


def test_postgres_fts_retrieval_preserves_the_search_result_without_an_openai_key() -> None:
    repository = RecordingRepository()
    with (
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag"),
        patch.object(settings, "OPENAI_API_KEY", ""),
    ):
        result = asyncio.run(HybridRetriever(repository=repository).retrieve("토양 관리"))

    assert result.insufficient_evidence is False
    assert result.chunks[0].citation()["citationId"] == "rag:chunk-1"
    assert repository.arguments is not None
    assert repository.arguments["query_text"] == "토양 관리"
    assert repository.arguments["top_k"] == settings.RAG_TOP_K


def test_knowledge_tool_uses_postgres_fts_without_an_openai_key() -> None:
    repository = RecordingRepository()
    with (
        patch.object(settings, "RAG_DATABASE_URL", "postgresql://rag"),
        patch.object(settings, "OPENAI_API_KEY", ""),
    ):
        result = asyncio.run(
            AgentToolExecutor(HybridRetriever(repository=repository)).execute(
                name="search_approved_knowledge",
                arguments={"query": "토양 관리"},
                fact_package=FactPackage(requestId="rag-tool", question="토양 관리"),
            )
        )

    assert result.status == "ok"
    assert [citation.citation_id for citation in result.citations] == ["rag:chunk-1"]


class HybridRecordingRepository(RecordingRepository):
    async def search_hybrid(self, **arguments: object) -> RetrievalExecution:
        self.arguments = arguments
        return RetrievalExecution(
            chunks=[RetrievedChunk(
                chunk_id="chunk-hybrid", document_id="document-1", source_id="source-1",
                source_name="농사로", canonical_url="https://example.go.kr/soil",
                title="토양 관리", content="토양 상태를 확인하세요.", score=0.04, metadata={},
                retrieval_paths=("lexical", "semantic"),
            )],
            mode="hybrid",
            candidate_count=1,
        )


class StaticEmbeddingProvider:
    model = "text-embedding-3-small"
    dimensions = 1536

    async def embed_query(self, query: str) -> tuple[float, ...]:
        assert query == "토양 관리"
        return (0.1,) * self.dimensions


def test_postgres_retriever_uses_hybrid_search_when_the_embedding_contract_is_available() -> None:
    repository = HybridRecordingRepository()

    result = asyncio.run(HybridRetriever(
        repository=repository,
        embedding_provider=StaticEmbeddingProvider(),  # type: ignore[arg-type]
    ).retrieve("토양 관리"))

    assert result.mode == "hybrid"
    assert result.candidate_count == 1
    assert repository.arguments is not None
    assert repository.arguments["embedding_model"] == "text-embedding-3-small"
    assert repository.arguments["query_embedding"] == (0.1,) * 1536


class TraceRecordingRepository(RecordingRepository):
    def __init__(self) -> None:
        super().__init__()
        self.trace: dict[str, object] | None = None

    async def record_retrieval_trace(self, **arguments: object) -> None:
        self.trace = arguments

    async def record_agent_execution(self, **arguments: object) -> None:
        self.agent_execution = arguments


def test_postgres_retriever_records_a_privacy_safe_trace_for_an_agent_request() -> None:
    repository = TraceRecordingRepository()
    with patch.object(settings, "OPENAI_API_KEY", ""):
        result = asyncio.run(HybridRetriever(repository=repository).retrieve(
            "토양 관리",
            request_id="request-123",
        ))

    assert result.mode == "lexical"
    assert repository.trace is not None
    assert repository.trace["request_id"] == "request-123"
    assert repository.trace["query_text"] == "토양 관리"
    assert repository.trace["retrieval_mode"] == "lexical"


def test_postgres_retriever_records_only_aggregate_agent_execution_metrics() -> None:
    repository = TraceRecordingRepository()
    telemetry = AgentExecutionTelemetry(
        terminal_status="completed",
        terminal_reason="completed",
        model_turn_count=2,
        tool_call_count=1,
        tool_non_success_count=0,
        citation_count=1,
        answer_char_count=120,
        total_latency_ms=240,
        model_latency_ms=200,
        tool_latency_ms=40,
        tool_statuses=("ok",),
    )

    with (
        patch.object(settings, "OPENAI_MODEL", "test-grounded-model"),
        patch.object(settings, "AGENT_PIPELINE_VERSION", "sectioned-citations-v1", create=True),
    ):
        recorded = asyncio.run(HybridRetriever(repository=repository).record_agent_execution(
            request_id="agent-request-123",
            telemetry=telemetry,
        ))

    assert recorded is True
    assert repository.agent_execution is not None
    assert repository.agent_execution["request_id"] == "agent-request-123"
    assert repository.agent_execution["terminal_status"] == "completed"
    assert repository.agent_execution["tool_statuses"] == ["ok"]
    assert repository.agent_execution["model_name"] == "test-grounded-model"
    assert repository.agent_execution["pipeline_version"] == "sectioned-citations-v1"
    assert repository.agent_execution["measurement_scope"] == "runtime_local"
    assert "answer" not in repository.agent_execution
    assert "question" not in repository.agent_execution


def test_postgres_retriever_reports_when_agent_execution_metrics_cannot_be_persisted() -> None:
    telemetry = AgentExecutionTelemetry(
        terminal_status="needs_context",
        terminal_reason="model_needs_context",
        model_turn_count=1,
        tool_call_count=0,
        tool_non_success_count=0,
        citation_count=0,
        answer_char_count=20,
        total_latency_ms=120,
        model_latency_ms=120,
        tool_latency_ms=0,
    )

    recorded = asyncio.run(HybridRetriever(repository=RecordingRepository()).record_agent_execution(
        request_id="agent-request-123",
        telemetry=telemetry,
    ))

    assert recorded is False
