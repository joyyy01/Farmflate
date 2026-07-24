from __future__ import annotations

import json
from typing import Any, AsyncGenerator, Iterable
from uuid import uuid4

import httpx
from langgraph.graph import END, START, StateGraph
from typing_extensions import TypedDict

from app.core.config import settings
from app.schemas.chat import (
    AgentTaskRequest,
    AgentTaskResponse,
    ChatPageContext,
    ChatRequest,
    ChatResponse,
    GroundingSource,
)


class FarmAgentState(TypedDict, total=False):
    request: ChatRequest
    intent: str
    facts: dict[str, Any]
    fallback: str
    reply: str
    trace: list[str]


class AIService:
    """A report-grounded assistant with an optional OpenAI synthesis step.

    Facts are selected from the current page context before any model is called.
    That keeps the fallback useful without credentials and prevents a configured
    model from inventing a report, a public-data request, or a field condition.
    """

    _RISK_WORDS = ("위험", "주의", "조심", "병", "피해", "재해")
    _CROP_WORDS = ("작물", "심", "재배", "추천", "감자", "배", "오이", "상추", "사과")
    _WHY_WORDS = ("왜", "이유", "근거", "분석", "점수")

    def __init__(self) -> None:
        builder = StateGraph(FarmAgentState)
        builder.add_node("classify_request", self._classify_request)
        builder.add_node("collect_page_evidence", self._collect_page_evidence)
        builder.add_node("draft_grounded_answer", self._draft_grounded_answer)
        builder.add_node("verify_grounding", self._verify_grounding)
        builder.add_edge(START, "classify_request")
        builder.add_edge("classify_request", "collect_page_evidence")
        builder.add_edge("collect_page_evidence", "draft_grounded_answer")
        builder.add_edge("draft_grounded_answer", "verify_grounding")
        builder.add_edge("verify_grounding", END)
        self._agent_graph = builder.compile()

    async def process_chat(self, request: ChatRequest) -> ChatResponse:
        state = await self._run_agent_graph(request)
        return self._response_from_state(state)

    async def _run_agent_graph(self, request: ChatRequest) -> FarmAgentState:
        return await self._agent_graph.ainvoke({"request": request, "trace": []})

    def _response_from_state(self, state: FarmAgentState) -> ChatResponse:
        facts = state["facts"]
        return ChatResponse(
            reply=state["reply"],
            status="grounded" if facts["has_report"] else "needs_context",
            sources=facts["sources"],
            used_context=facts["used_context"],
            agent_steps=state.get("trace", []),
        )

    async def stream_chat_response(self, request: ChatRequest) -> AsyncGenerator[str, None]:
        """Stream one already-grounded answer; no simulated delay or fake stages."""
        response = await self.process_chat(request)
        for chunk in self._chunks(response.reply, 64):
            yield f"event: message\ndata: {json.dumps({'delta': chunk}, ensure_ascii=False)}\n\n"
        yield f"event: done\ndata: {response.model_dump_json()}\n\n"

    async def execute_agent_task(self, request: AgentTaskRequest) -> AgentTaskResponse:
        state = await self._run_agent_graph(ChatRequest(message=request.task, history=request.history, context=request.context))
        response = self._response_from_state(state)
        return AgentTaskResponse(
            task_id=str(uuid4()),
            status="completed" if response.status == "grounded" else "needs_context",
            result=response.reply,
            steps_taken=response.agent_steps,
            sources=response.sources,
        )

    def _classify_request(self, state: FarmAgentState) -> dict[str, Any]:
        intent = self._classify_intent(state["request"].message)
        return {"intent": intent, "trace": [f"질문 의도를 ‘{intent}’로 분류했습니다."]}

    def _collect_page_evidence(self, state: FarmAgentState) -> dict[str, Any]:
        facts = self._extract_facts(state["request"].context)
        report_status = "완료된 분석 리포트의 근거를 확인했습니다." if facts["has_report"] else "완료된 분석 리포트가 없음을 확인했습니다."
        return {"facts": facts, "trace": [*state["trace"], report_status]}

    def _draft_grounded_answer(self, state: FarmAgentState) -> dict[str, Any]:
        fallback = self._build_grounded_reply(state["request"].message, state["facts"], state["intent"])
        return {"fallback": fallback, "trace": [*state["trace"], "확인된 근거만으로 답변 초안을 만들었습니다."]}

    async def _verify_grounding(self, state: FarmAgentState) -> dict[str, Any]:
        reply = await self._optionally_synthesize(state["request"], state["facts"], state["fallback"])
        reply = reply.strip() if isinstance(reply, str) else state["fallback"]
        if not reply or len(reply) > 1_600:
            reply = state["fallback"]
        return {"reply": reply, "trace": [*state["trace"], "답변의 화면 근거와 응답 길이를 검증했습니다."]}

    def _classify_intent(self, message: str) -> str:
        lowered = message.replace(" ", "")
        if any(word in lowered for word in self._RISK_WORDS):
            return "위험·관리 확인"
        if any(word in lowered for word in self._CROP_WORDS):
            return "작물 추천 확인"
        if any(word in lowered for word in self._WHY_WORDS):
            return "리포트 근거 확인"
        return "현재 리포트 요약"

    async def _optionally_synthesize(self, request: ChatRequest, facts: dict[str, Any], fallback: str) -> str:
        """Use OpenAI only when explicitly configured; a timeout always falls back safely."""
        if not settings.OPENAI_API_KEY or settings.LLM_PROVIDER.lower() != "openai":
            return fallback

        evidence = self._model_evidence(facts)
        if not evidence:
            return fallback

        messages = [
            {
                "role": "system",
                "content": (
                    "당신은 Farmflate 농사 안내 도우미입니다. 반드시 제공된 근거만 사용해 한국어로 답하세요. "
                    "제공되지 않은 날씨, 토양 수치, 병해, 작물 적합도, 공공 API 호출 결과를 추정하거나 만들어 내지 마세요. "
                    "의료·농약 처방처럼 단정적인 지시는 피하고, 근거와 다음 확인 행동을 짧게 설명하세요."
                ),
            },
            *[{"role": item.role, "content": item.content} for item in request.history[-8:]],
            {"role": "user", "content": f"질문: {request.message}\n\n현재 화면 근거:\n{evidence}"},
        ]
        payload = {"model": settings.OPENAI_MODEL, "messages": messages, "temperature": request.temperature}
        try:
            async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
                result = await client.post(
                    f"{settings.OPENAI_BASE_URL.rstrip('/')}/chat/completions",
                    headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                    json=payload,
                )
                result.raise_for_status()
                content = result.json()["choices"][0]["message"]["content"].strip()
                return content or fallback
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError):
            return fallback

    def _extract_facts(self, context: ChatPageContext | None) -> dict[str, Any]:
        context = context or ChatPageContext()
        report = context.report or {}
        home = context.home or {}
        region = context.region or self._region_from_report(report)
        crop = context.selected_crop
        score = self._first_value(report, "score", "overallScore", "environmentScore")
        recommendations = self._records(report.get("recommendedCrops") or report.get("recommendations"), 3)
        risks = self._records(report.get("keyRisks") or report.get("risks"), 3)
        features = self._strings(report.get("environmentFeatures"), 3)
        today_action = home.get("todayAction") if isinstance(home.get("todayAction"), dict) else None
        sources = self._build_sources(report, region)
        has_report = bool(score is not None or recommendations or risks or features)
        used_context = []
        if region:
            used_context.append("선택 지역")
        if has_report:
            used_context.append("완료된 지역 분석 리포트")
        if context.fields:
            used_context.append("마이팜 등록 농작물")
        if today_action:
            used_context.append("오늘 할 일")
        return {
            "region": region,
            "crop": crop,
            "score": score,
            "recommendations": recommendations,
            "risks": risks,
            "features": features,
            "today_action": today_action,
            "fields": context.fields,
            "has_report": has_report,
            "sources": sources,
            "used_context": used_context,
        }

    def _build_grounded_reply(self, message: str, facts: dict[str, Any], intent: str) -> str:
        if not facts["has_report"]:
            region_text = f"{facts['region']}의 " if facts["region"] else ""
            return (
                f"{region_text}완료된 분석 리포트를 아직 확인하지 못했습니다. "
                "지역 환경 분석이 완료된 뒤 다시 질문해 주시면, 리포트의 점수·위험 요소·추천 작물 근거를 바탕으로 안내할게요."
            )

        intro = f"{facts['region']} 분석 리포트 기준으로 " if facts["region"] else "현재 분석 리포트 기준으로 "
        if intent == "위험·관리 확인":
            risk_text = self._risk_summary(facts["risks"])
            return intro + (risk_text or "표시된 핵심 위험 항목이 없습니다.") + self._next_check(facts)
        if intent == "작물 추천 확인":
            crop_text = self._crop_summary(facts["recommendations"])
            return intro + (crop_text or "추천 작물 데이터가 아직 없습니다.") + self._next_check(facts)
        if intent == "리포트 근거 확인":
            score = f"기본 적합도는 {facts['score']}점입니다. " if facts["score"] is not None else ""
            feature_text = " ".join(facts["features"][:2])
            return intro + score + (feature_text or self._crop_summary(facts["recommendations"]) or "리포트의 세부 근거를 확인해 주세요.") + self._next_check(facts)

        parts = []
        if facts["score"] is not None:
            parts.append(f"기본 적합도는 {facts['score']}점입니다.")
        crop_text = self._crop_summary(facts["recommendations"])
        if crop_text:
            parts.append(crop_text)
        risk_text = self._risk_summary(facts["risks"])
        if risk_text:
            parts.append(risk_text)
        return intro + " ".join(parts[:3]) + self._next_check(facts)

    @staticmethod
    def _next_check(facts: dict[str, Any]) -> str:
        action = facts.get("today_action") or {}
        title = action.get("title") if isinstance(action, dict) else None
        return f" 현재 확인할 일은 ‘{title}’입니다." if isinstance(title, str) and title.strip() else " 리포트의 데이터 기준일과 위험 카드도 함께 확인해 주세요."

    @staticmethod
    def _first_value(record: dict[str, Any], *keys: str) -> Any:
        for key in keys:
            value = record.get(key)
            if isinstance(value, (str, int, float)) and str(value).strip():
                return value
        summary = record.get("summary")
        return AIService._first_value(summary, *keys) if isinstance(summary, dict) else None

    @staticmethod
    def _records(value: Any, limit: int) -> list[dict[str, Any]]:
        return [item for item in value if isinstance(item, dict)][:limit] if isinstance(value, list) else []

    @staticmethod
    def _strings(value: Any, limit: int) -> list[str]:
        if not isinstance(value, list):
            return []
        result = []
        for item in value:
            if isinstance(item, str) and item.strip():
                result.append(item.strip())
            elif isinstance(item, dict):
                text = item.get("description") or item.get("label") or item.get("title")
                if isinstance(text, str) and text.strip():
                    result.append(text.strip())
        return result[:limit]

    @staticmethod
    def _region_from_report(report: dict[str, Any]) -> str | None:
        region = report.get("region")
        if not isinstance(region, dict):
            return None
        parts = [region.get(key) for key in ("sidoName", "sigunguName")]
        text = " ".join(str(value).strip() for value in parts if isinstance(value, str) and value.strip())
        return text or None

    def _build_sources(self, report: dict[str, Any], region: str | None) -> list[GroundingSource]:
        sources = []
        if report:
            observed_at = self._first_value(report, "analyzedAt", "createdAt", "dataDate", "analysisDate")
            sources.append(GroundingSource(title="현재 화면의 지역 분석 리포트", detail=region, observed_at=str(observed_at) if observed_at else None))
        return sources

    @staticmethod
    def _crop_summary(records: list[dict[str, Any]]) -> str:
        labels = []
        for item in records:
            name = item.get("cropName") or item.get("name")
            score = item.get("score")
            if isinstance(name, str) and name.strip():
                labels.append(f"{name.strip()}{f' {score}점' if isinstance(score, (int, float)) else ''}")
        return f"추천 작물은 {', '.join(labels)}입니다." if labels else ""

    @staticmethod
    def _risk_summary(records: list[dict[str, Any]]) -> str:
        labels = []
        for item in records:
            title = item.get("title") or item.get("riskName") or item.get("riskCode")
            action = item.get("recommendedAction") or item.get("description")
            if isinstance(title, str) and title.strip():
                label = title.strip()
                if isinstance(action, str) and action.strip():
                    label += f": {action.strip()}"
                labels.append(label)
        return f"우선 확인할 위험은 {' / '.join(labels)}입니다." if labels else ""

    @staticmethod
    def _model_evidence(facts: dict[str, Any]) -> str:
        blocks = []
        if facts["region"]:
            blocks.append(f"지역: {facts['region']}")
        if facts["score"] is not None:
            blocks.append(f"기본 적합도: {facts['score']}")
        if facts["recommendations"]:
            blocks.append("추천 작물: " + AIService._crop_summary(facts["recommendations"]))
        if facts["risks"]:
            blocks.append("핵심 위험: " + AIService._risk_summary(facts["risks"]))
        if facts["features"]:
            blocks.append("환경 특징: " + " | ".join(facts["features"]))
        return "\n".join(blocks)

    @staticmethod
    def _chunks(value: str, width: int) -> Iterable[str]:
        return (value[index : index + width] for index in range(0, len(value), width))


ai_service = AIService()
