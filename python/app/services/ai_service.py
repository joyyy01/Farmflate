from __future__ import annotations

import json
from typing import Any, AsyncGenerator
from uuid import uuid4

from app.agent.contracts import ToolCitation
from app.agent.runner import GroundedAgent
from app.core.config import settings
from app.rag.retriever import rag_retriever
from app.schemas.chat import (
    AgentRunRequest,
    AgentRunResponse,
    AgentTaskRequest,
    AgentTaskResponse,
    ChatRequest,
    ChatResponse,
    FactPackage,
    GroundingSource,
    StructuredAnswer,
)


def aggregate_grounding_sources(citations: list[ToolCitation]) -> list[dict[str, Any]]:
    """Present one source per document while retaining chunk-level evidence IDs."""
    grouped: dict[tuple[str, str | None], dict[str, Any]] = {}
    for citation in citations:
        key = (citation.title, citation.source_url)
        source = grouped.setdefault(key, {
            "sourceId": citation.citation_id,
            "title": citation.title,
            "sourceUrl": citation.source_url,
            "citationIds": [],
            "evidenceCount": 0,
        })
        source["citationIds"].append(citation.citation_id)
        source["evidenceCount"] += 1
    return list(grouped.values())


class AIService:
    """HTTP-facing facade for one grounded Agent path."""

    def __init__(self) -> None:
        self._grounded_agent = GroundedAgent()

    async def run_agent(self, request: AgentRunRequest) -> AgentRunResponse:
        fact_package = request.fact_package
        if not settings.OPENAI_API_KEY:
            return self._agent_unavailable(fact_package)
        result = await self._grounded_agent.run(fact_package)
        if result.telemetry is not None:
            await rag_retriever.record_agent_execution(
                request_id=fact_package.requestId,
                telemetry=result.telemetry,
                measurement_scope="runtime_local",
            )
        if result.status == "failed":
            return self._agent_unavailable(fact_package)
        sources = aggregate_grounding_sources(result.citations)
        return AgentRunResponse(
            requestId=fact_package.requestId,
            status=result.status,
            answer=StructuredAnswer(
                answer=result.answer,
                basisType="CURRENT_REPORT" if result.status == "completed" else "INSUFFICIENT_EVIDENCE",
                usedSourceIds=result.citation_ids,
                safetyNotice=result.safety_notice,
            ),
            sources=sources,
            trace=["승인된 근거를 확인했습니다."] if result.status == "completed" else ["근거가 부족해 답변을 보류했습니다."],
        )

    @staticmethod
    def _agent_unavailable(fact_package: FactPackage) -> AgentRunResponse:
        return AgentRunResponse(
            requestId=fact_package.requestId,
            status="needs_context",
            answer=StructuredAnswer(
                answer="현재 검증 가능한 답변을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                basisType="INSUFFICIENT_EVIDENCE",
            ),
            sources=[],
            trace=["현재 AI 응답을 완료하지 못했습니다."],
        )

    async def process_chat(self, request: ChatRequest) -> ChatResponse:
        response = await self.run_agent(AgentRunRequest(fact_package=self._chat_request_to_fact_package(request)))
        sources = [
            GroundingSource(
                title=source.get("title") or (source.get("provider", "") + " " + source.get("service", "")).strip(),
                detail=source.get("dataDate"),
                observed_at=source.get("dataDate"),
                source_url=source.get("sourceUrl"),
                evidence_count=int(source.get("evidenceCount") or 1),
            )
            for source in response.sources
        ]
        grounded = response.status == "completed" and bool(sources) and bool(response.answer.usedSourceIds)
        if grounded:
            steps = ["승인된 근거를 확인해 답변을 만들었습니다."]
        elif response.status == "completed":
            steps = ["인용 가능한 근거 출처가 없어 답변을 보류했습니다."]
        else:
            steps = list(response.trace)
        return ChatResponse(
            reply=response.answer.answer,
            status="grounded" if grounded else "needs_context",
            sources=sources,
            used_context=response.answer.usedFactIds,
            agent_steps=steps,
        )

    async def stream_chat_response(self, request: ChatRequest) -> AsyncGenerator[str, None]:
        try:
            response = await self.process_chat(request)
        except Exception:
            yield f"event: error\ndata: {json.dumps({'code': 'chat_unavailable'}, ensure_ascii=False)}\n\n"
            return
        yield f"event: done\ndata: {response.model_dump_json()}\n\n"

    async def execute_agent_task(self, request: AgentTaskRequest) -> AgentTaskResponse:
        response = await self.process_chat(
            ChatRequest(message=request.task, history=request.history, context=request.context)
        )
        return AgentTaskResponse(
            task_id=str(uuid4()),
            status="completed" if response.status == "grounded" else "needs_context",
            result=response.reply,
            steps_taken=response.agent_steps,
            sources=response.sources,
        )

    @staticmethod
    def _chat_request_to_fact_package(request: ChatRequest) -> FactPackage:
        context = request.context or {}
        facts: dict[str, Any] = {}
        if isinstance(context, dict):
            report = context.get("report", {})
            if isinstance(report, dict):
                score = report.get("regionScore") or report.get("score")
                if score is not None:
                    facts["region.score"] = score
                grade = report.get("regionGrade") or report.get("grade")
                if grade:
                    facts["region.grade"] = grade
                region = report.get("region", {})
                if isinstance(region, dict):
                    name = " ".join(part for part in (region.get("sidoName", ""), region.get("sigunguName", "")) if part)
                    if name:
                        facts["region.name"] = name
                crops = report.get("recommendedCrops", [])
                if isinstance(crops, list):
                    for index, crop in enumerate(crops[:3], 1):
                        if isinstance(crop, dict):
                            facts[f"crop.{index}.name"] = crop.get("cropName", crop.get("name", ""))
                            facts[f"crop.{index}.score"] = crop.get("score")
                risks = report.get("topRisks", report.get("keyRisks", []))
                if isinstance(risks, list):
                    for index, risk in enumerate(risks[:3], 1):
                        if isinstance(risk, dict):
                            facts[f"risk.{index}.code"] = risk.get("riskCode", risk.get("code", ""))
                            facts[f"risk.{index}.title"] = risk.get("title", risk.get("riskName", ""))
                            action = risk.get("recommendedAction", risk.get("description", ""))
                            if action:
                                facts[f"risk.{index}.action.1"] = action
        prefixes = sorted({f"{fact_key.split('.', 1)[0]}." for fact_key in facts})
        sources = [{
            "sourceId": "farmflate:current-report",
            "provider": "Farmflate 현재 분석 리포트",
            "factKeyPrefixes": prefixes,
        }] if prefixes else []
        return FactPackage(
            requestId=str(uuid4()),
            question=request.message,
            history=[{"role": message.role, "content": message.content} for message in request.history],
            context=context if isinstance(context, dict) else {},
            facts=facts,
            sources=sources,
        )


ai_service = AIService()
