from __future__ import annotations

import json
import re
from typing import Any, AsyncGenerator, Iterable
from uuid import uuid4

import httpx
from langgraph.graph import END, START, StateGraph
from typing_extensions import TypedDict

from app.core.config import settings
from app.schemas.chat import (
    AgentRunRequest,
    AgentRunResponse,
    AgentTaskRequest,
    AgentTaskResponse,
    ChatRequest,
    ChatResponse,
    FieldGuidanceRequest,
    FieldGuidanceResponse,
    FieldGuidanceTask,
    FactPackage,
    GroundingSource,
    StructuredAnswer,
)
from app.services.tools import (
    explain_agricultural_term,
    get_field_report,
    get_region_analysis,
    get_report_sources,
    search_official_guidance,
    get_crop_profile,
    get_seasonal_advice,
    get_risk_guide,
    compare_crops,
    CROP_PROFILES,
    RISK_GUIDES,
)


class AgentState(TypedDict, total=False):
    fact_package: FactPackage
    intent: str
    selected_tools: list[str]
    tool_results: dict[str, Any]
    structured_answer: StructuredAnswer
    deterministic_answer: StructuredAnswer
    validation_passed: bool
    final_answer: StructuredAnswer
    final_sources: list[dict[str, Any]]
    trace: list[str]


class AIService:
    _RISK_WORDS = ("위험", "주의", "조심", "병", "피해", "재해", "호우", "폭염", "강풍", "저온", "서리", "가뭄", "태풍", "역병", "노균")
    _CROP_WORDS = ("작물", "심", "재배", "추천", "감자", "배", "오이", "상추", "사과", "적합", "뭘", "어떤")
    _WHY_WORDS = ("왜", "이유", "근거", "분석", "점수", "어떻게", "판정", "산출")
    _TERM_WORDS = ("뜻", "의미", "뭐", "무엇", "용어", "EC", "pH", "유기물", "질소", "인산", "칼륨", "칼슘", "마그네슘", "배수", "적산온도", "생육적온", "도장", "엽소", "습해", "연작")
    _GUIDANCE_WORDS = ("농사로", "공식", "가이드", "관리법", "안내", "지침", "요령", "방법")
    _UNSUPPORTED_WORDS = ("바꿔", "변경", "수정", "조정해", "높여", "낮춰", "삭제", "취소")
    _WATER_WORDS = ("물주기", "물줘", "물 줘", "급수", "관수", "물주", "물 주")
    _SEASON_WORDS = ("계절", "시기", "달", "월", "언제", "파종", "정식", "수확", "전정", "가지치기", "심는", "거두", "심어야", "심어")
    _COMPARE_WORDS = ("비교", "차이", "vs", "어느", "골라", "선택", "낫")
    _SOIL_WORDS = ("토양", "흙", "산도", "비료", "퇴비", "석회", "양분", "거름")

    def __init__(self) -> None:
        builder = StateGraph(AgentState)
        builder.add_node("validate_request", self._validate_request)
        builder.add_node("classify_intent", self._classify_intent)
        builder.add_node("select_tools", self._select_tools)
        builder.add_node("execute_tools", self._execute_tools)
        builder.add_node("compose_structured_answer", self._compose_structured_answer)
        builder.add_node("validate_facts_and_sources", self._validate_facts_and_sources)
        builder.add_node("fallback_or_return", self._fallback_or_return)
        builder.add_edge(START, "validate_request")
        builder.add_edge("validate_request", "classify_intent")
        builder.add_edge("classify_intent", "select_tools")
        builder.add_edge("select_tools", "execute_tools")
        builder.add_edge("execute_tools", "compose_structured_answer")
        builder.add_edge("compose_structured_answer", "validate_facts_and_sources")
        builder.add_edge("validate_facts_and_sources", "fallback_or_return")
        builder.add_edge("fallback_or_return", END)
        self._agent_graph = builder.compile()

    async def run_agent(self, request: AgentRunRequest) -> AgentRunResponse:
        fp = request.fact_package
        state = await self._agent_graph.ainvoke({
            "fact_package": fp,
            "trace": [],
        })
        return AgentRunResponse(
            requestId=fp.requestId,
            status="completed" if state.get("validation_passed") else "fallback",
            answer=state["final_answer"],
            sources=state.get("final_sources", []),
        )

    async def process_chat(self, request: ChatRequest) -> ChatResponse:
        fp = self._chat_request_to_fact_package(request)
        agent_request = AgentRunRequest(fact_package=fp)
        response = await self.run_agent(agent_request)
        sources = [
            GroundingSource(
                title=s.get("provider", "") + " " + s.get("service", ""),
                detail=s.get("dataDate"),
                observed_at=s.get("dataDate"),
                source_url=s.get("sourceUrl"),
            )
            for s in response.sources
        ]
        return ChatResponse(
            reply=response.answer.answer,
            status="grounded" if response.status == "completed" else "needs_context",
            sources=sources,
            used_context=response.answer.usedFactIds,
            agent_steps=[],
        )

    async def stream_chat_response(self, request: ChatRequest) -> AsyncGenerator[str, None]:
        response = await self.process_chat(request)
        for chunk in self._chunks(response.reply, 64):
            yield f"event: message\ndata: {json.dumps({'delta': chunk}, ensure_ascii=False)}\n\n"
        yield f"event: done\ndata: {response.model_dump_json()}\n\n"

    async def execute_agent_task(self, request: AgentTaskRequest) -> AgentTaskResponse:
        chat_request = ChatRequest(message=request.task, history=request.history)
        response = await self.process_chat(chat_request)
        return AgentTaskResponse(
            task_id=str(uuid4()),
            status="completed" if response.status == "grounded" else "needs_context",
            result=response.reply,
            steps_taken=response.agent_steps,
            sources=response.sources,
        )

    async def generate_field_guidance(self, request: FieldGuidanceRequest) -> FieldGuidanceResponse:
        """Return a strict, fact-only field-report contract for the Java narrator.

        The previous integration sent a JSON-only prompt through the
        conversational task endpoint. That endpoint correctly produced chat
        prose, which the Java narrator then rejected as malformed JSON. This
        endpoint only reshapes verified rule-engine candidates, so it cannot
        invent a task or a numeric condition.
        """
        facts = request.facts if isinstance(request.facts, dict) else {}
        raw_tasks = facts.get("tasks") if isinstance(facts.get("tasks"), list) else []
        tasks: list[FieldGuidanceTask] = []
        for raw_task in raw_tasks:
            if not isinstance(raw_task, dict):
                continue
            key = str(raw_task.get("key") or "").strip()
            title = str(raw_task.get("title") or "").strip()
            description = str(raw_task.get("description") or "").strip()
            if key and title and description:
                tasks.append(FieldGuidanceTask(key=key, title=title[:120], description=description[:300]))

        raw_alerts = facts.get("alerts") if isinstance(facts.get("alerts"), list) else []
        alert_titles = [
            str(alert.get("title") or "").strip()
            for alert in raw_alerts
            if isinstance(alert, dict) and str(alert.get("title") or "").strip()
        ]
        crop_name = str(facts.get("cropName") or "작물").strip() or "작물"
        headline = (alert_titles[0] if alert_titles else (tasks[0].title if tasks else f"오늘 {crop_name} 상태를 확인하세요"))[:80]
        headline_description = (
            tasks[0].description if tasks
            else "현재 확인된 환경 정보를 바탕으로 작물 상태를 점검해 주세요."
        )[:300]
        summary_items = [task.title for task in tasks[:2]] or alert_titles[:2]
        reasoning_summary = (
            "검증된 환경 분석 결과를 바탕으로 " + " · ".join(summary_items) + "을 우선 안내합니다."
            if summary_items else "검증된 환경 분석 결과를 바탕으로 오늘의 작물 상태를 안내합니다."
        )[:500]
        return FieldGuidanceResponse(
            headline=headline,
            headlineDescription=headline_description,
            tasks=tasks,
            reasoningSummary=reasoning_summary,
        )

    def _chat_request_to_fact_package(self, request: ChatRequest) -> FactPackage:
        ctx = request.context or {}
        facts = {}
        sources = []
        if isinstance(ctx, dict):
            report = ctx.get("report", {})
            if isinstance(report, dict):
                score = report.get("regionScore") or report.get("score")
                if score is not None:
                    facts["region.score"] = score
                grade = report.get("regionGrade") or report.get("grade")
                if grade:
                    facts["region.grade"] = grade
                region = report.get("region", {})
                if isinstance(region, dict):
                    parts = [region.get("sidoName", ""), region.get("sigunguName", "")]
                    name = " ".join(p for p in parts if p)
                    if name:
                        facts["region.name"] = name
                crops = report.get("recommendedCrops", [])
                if isinstance(crops, list):
                    for i, crop in enumerate(crops[:3], 1):
                        if isinstance(crop, dict):
                            facts[f"crop.{i}.name"] = crop.get("cropName", crop.get("name", ""))
                            facts[f"crop.{i}.score"] = crop.get("score")
                risks = report.get("topRisks", report.get("keyRisks", []))
                if isinstance(risks, list):
                    for i, risk in enumerate(risks[:3], 1):
                        if isinstance(risk, dict):
                            facts[f"risk.{i}.code"] = risk.get("riskCode", risk.get("code", ""))
                            facts[f"risk.{i}.title"] = risk.get("title", risk.get("riskName", ""))
                            action = risk.get("recommendedAction", risk.get("description", ""))
                            if action:
                                facts[f"risk.{i}.action.1"] = action
        return FactPackage(
            requestId=str(uuid4()),
            question=request.message,
            history=[{"role": m.role, "content": m.content} for m in request.history],
            context=ctx if isinstance(ctx, dict) else {},
            facts=facts,
            sources=sources,
        )

    def _validate_request(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        if not fp.question or not fp.question.strip():
            raise ValueError("질문이 비어 있습니다.")
        return {"trace": ["요청을 검증했습니다."]}

    def _classify_intent(self, state: AgentState) -> dict[str, Any]:
        question = state["fact_package"].question
        intent = self._classify(question)
        return {"intent": intent, "trace": [*state["trace"], f"의도 분류: {intent}"]}

    def _classify(self, message: str) -> str:
        lowered = message.replace(" ", "")
        if any(w in lowered for w in self._UNSUPPORTED_WORDS):
            return "UNSUPPORTED_ACTION"
        if any(w in lowered for w in self._TERM_WORDS):
            return "TERM_EXPLANATION"
        # Evidence questions must win over the crop-name keyword.  Otherwise
        # "상추 점수는 왜 낮아요?" was incorrectly treated as a generic crop
        # recommendation instead of an explanation of the user's report.
        if any(w in lowered for w in self._WHY_WORDS):
            return "REPORT_REASON"
        if any(w in lowered for w in self._WATER_WORDS):
            return "WATERING_GUIDANCE"
        if any(w in lowered for w in self._SEASON_WORDS):
            return "SEASONAL_ADVICE"
        if any(w in lowered for w in self._COMPARE_WORDS):
            return "CROP_COMPARISON"
        if any(w in lowered for w in self._SOIL_WORDS):
            return "SOIL_ADVICE"
        if any(w in lowered for w in self._GUIDANCE_WORDS):
            return "OFFICIAL_GUIDANCE"
        if any(w in lowered for w in self._RISK_WORDS):
            return "RISK_EXPLANATION"
        if any(w in lowered for w in self._CROP_WORDS):
            return "CROP_RECOMMENDATION"
        return "GENERAL_INFORMATION"

    def _select_tools(self, state: AgentState) -> dict[str, Any]:
        intent = state["intent"]
        tool_map: dict[str, list[str]] = {
            "REPORT_REASON": ["get_region_analysis", "get_report_sources"],
            "RISK_EXPLANATION": ["get_region_analysis", "get_risk_guide", "get_report_sources"],
            "CROP_RECOMMENDATION": ["get_region_analysis", "get_crop_profile", "get_report_sources"],
            "CROP_COMPARISON": ["get_region_analysis", "compare_crops", "get_report_sources"],
            "TERM_EXPLANATION": ["explain_agricultural_term"],
            "SEASONAL_ADVICE": ["get_region_analysis", "get_seasonal_advice", "get_report_sources"],
            "WATERING_GUIDANCE": ["get_region_analysis", "get_crop_profile", "get_report_sources"],
            "SOIL_ADVICE": ["get_region_analysis", "get_crop_profile", "get_report_sources"],
            "OFFICIAL_GUIDANCE": ["search_official_guidance", "get_report_sources"],
            "GENERAL_INFORMATION": ["get_region_analysis", "get_report_sources"],
            "UNSUPPORTED_ACTION": [],
        }
        selected = list(tool_map.get(intent, ["get_region_analysis", "get_report_sources"]))
        # A field dashboard conversation should have access to the persisted
        # snapshot too. It is scoped by the Spring service before it reaches
        # the agent, so this adds context without granting data access.
        if any(key.startswith("field.") for key in state["fact_package"].facts) and "get_field_report" not in selected:
            selected.append("get_field_report")
        return {"selected_tools": selected, "trace": [*state["trace"], f"도구 선택: {selected}"]}

    def _execute_tools(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        results: dict[str, Any] = {}
        for tool_name in state.get("selected_tools", []):
            if tool_name == "get_region_analysis":
                results["region_analysis"] = get_region_analysis(fp.facts)
            elif tool_name == "get_report_sources":
                results["report_sources"] = get_report_sources(fp.sources)
            elif tool_name == "get_field_report":
                results["field_report"] = get_field_report(fp.facts)
            elif tool_name == "explain_agricultural_term":
                term = self._extract_term(fp.question)
                results["term_explanation"] = explain_agricultural_term(term)
            elif tool_name == "search_official_guidance":
                results["official_guidance"] = search_official_guidance(fp.facts, fp.sources)
            elif tool_name == "get_crop_profile":
                crop = self._extract_crop_name(fp.question, fp.facts)
                results["crop_profile"] = get_crop_profile(crop) if crop else None
            elif tool_name == "get_seasonal_advice":
                crop = self._extract_crop_name(fp.question, fp.facts)
                results["seasonal_advice"] = get_seasonal_advice(crop) if crop else None
            elif tool_name == "get_risk_guide":
                risk_code = fp.facts.get("risk.1.code", "")
                results["risk_guide"] = get_risk_guide(str(risk_code)) if risk_code else None
            elif tool_name == "compare_crops":
                results["crop_comparison"] = compare_crops(fp.facts)
        return {"tool_results": results, "trace": [*state["trace"], f"도구 실행: {list(results.keys())}"]}

    def _extract_crop_name(self, question: str, facts: dict[str, Any]) -> str | None:
        for code, profile in CROP_PROFILES.items():
            if profile["name"] in question:
                return profile["name"]
        top_crop = facts.get("crop.1.name")
        return str(top_crop) if top_crop else None

    def _extract_term(self, question: str) -> str:
        from app.services.tools import AGRICULTURAL_GLOSSARY
        for term in AGRICULTURAL_GLOSSARY:
            if term.lower() in question.lower():
                return term
        return question.strip()

    async def _compose_structured_answer(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        intent = state["intent"]
        tool_results = state.get("tool_results", {})

        if intent == "UNSUPPORTED_ACTION":
            answer = StructuredAnswer(
                answer="죄송합니다. 점수 변경, 데이터 수정과 같은 작업은 지원되지 않습니다. 리포트의 분석 결과에 대한 설명이나 농업 정보 안내를 도와드릴 수 있습니다.",
                basisType="UNSUPPORTED",
            )
            return {"structured_answer": answer, "trace": [*state["trace"], "지원 불가 응답 생성"]}

        if intent == "TERM_EXPLANATION" and "term_explanation" in tool_results:
            term_result = tool_results["term_explanation"]
            if term_result.get("found"):
                answer = StructuredAnswer(
                    answer=f"{term_result['term']}: {term_result['definition']}",
                    basisType="TERM_DEFINITION",
                )
                return {"structured_answer": answer, "trace": [*state["trace"], "용어 사전 응답 생성"]}

        region_analysis = tool_results.get("region_analysis", {})
        field_report = tool_results.get("field_report", {})
        if not region_analysis and not field_report:
            answer = StructuredAnswer(
                answer="완료된 분석 리포트를 아직 확인하지 못했습니다. 지역 환경 분석이 완료된 뒤 다시 질문해 주시면 리포트의 점수·위험 요소·추천 작물 근거를 바탕으로 안내할게요.",
                basisType="CURRENT_REPORT",
            )
            return {"structured_answer": answer, "trace": [*state["trace"], "리포트 없음 응답 생성"]}

        # Always compute the fact-only, intent-specific answer first. It is
        # never shown if the LLM succeeds and passes grounding validation,
        # but it is the fallback if the LLM is unavailable or hallucinates --
        # and unlike a single generic summary, it still varies by intent, so
        # different questions never collapse into the same canned reply.
        # A field report is a time-sensitive snapshot of the user's own crop.
        # It must remain the fallback priority even when this chat also has a
        # region analysis attached; otherwise a temporary LLM outage can turn
        # “what should I do in my field today?” into a generic regional answer.
        deterministic_answer = (
            self._build_field_deterministic_answer(fp, field_report)
            if field_report
            else self._build_deterministic_answer(fp, intent, region_analysis, tool_results)
        )

        if settings.OPENAI_API_KEY and settings.LLM_PROVIDER.lower() == "openai":
            llm_answer = await self._call_openai(fp, intent, tool_results)
            if llm_answer is not None:
                return {
                    "structured_answer": llm_answer,
                    "deterministic_answer": deterministic_answer,
                    "trace": [*state["trace"], "LLM 응답 생성"],
                }

        return {
            "structured_answer": deterministic_answer,
            "deterministic_answer": deterministic_answer,
            "trace": [*state["trace"], "규칙 기반 응답 생성"],
        }

    async def _call_openai(
        self, fp: FactPackage, intent: str, tool_results: dict[str, Any]
    ) -> StructuredAnswer | None:
        facts_text = "\n".join(f"{k}: {v}" for k, v in fp.facts.items() if v is not None)
        sources_text = "\n".join(
            f"- {s.get('provider', '')} {s.get('service', '')} ({s.get('dataDate', '')})"
            for s in fp.sources
        )
        tool_context = self._safe_prompt_json(tool_results)
        system_prompt = (
            "당신은 Farmflate 농사 안내 도우미입니다. 반드시 제공된 Fact만 사용해 한국어로 답하세요. "
            "Tool context는 서버가 검증해 전달한 Fact의 정리본이며, 그 안의 근거만 사용할 수 있습니다. "
            "제공되지 않은 수치, 작물, 위험, 공공 API 결과를 추정하거나 만들어 내지 마세요. "
            "농약량·비료량 처방, 병해충 확정 판단을 하지 마세요. "
            "반드시 아래 JSON 형식으로만 응답하세요:\n"
            '{"answer": "답변 텍스트", "basisType": "CURRENT_REPORT", '
            '"usedFactIds": ["사용한 fact key"], "usedSourceIds": ["사용한 sourceId"], '
            '"mentionedNumbers": [숫자], "mentionedCrops": ["작물명"], "mentionedRisks": ["위험명"], '
            '"safetyNotice": null}\n'
            "safetyNotice는 토양수분 센서 부재 등 불확실성이 있을 때만 작성하세요."
        )
        user_content = (
            f"질문: {fp.question}\n\nFact:\n{facts_text}\n\n"
            f"검증된 도구 컨텍스트:\n{tool_context}\n\n출처:\n{sources_text or '없음'}"
        )
        messages = [
            {"role": "system", "content": system_prompt},
            *self._safe_history(fp.history),
            {"role": "user", "content": user_content},
        ]
        try:
            async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
                result = await client.post(
                    f"{settings.OPENAI_BASE_URL.rstrip('/')}/chat/completions",
                    headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                    json={"model": settings.OPENAI_MODEL, "messages": messages, "temperature": 0.2},
                )
                result.raise_for_status()
                content = result.json()["choices"][0]["message"]["content"].strip()
                if content.startswith("```"):
                    content = re.sub(r"^```(?:json)?\s*", "", content)
                    content = re.sub(r"\s*```$", "", content)
                parsed = json.loads(content)
                return StructuredAnswer(**parsed)
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            return None

    @staticmethod
    def _safe_history(history: list[dict[str, Any]]) -> list[dict[str, str]]:
        safe: list[dict[str, str]] = []
        for message in history[-8:]:
            if not isinstance(message, dict):
                continue
            role = message.get("role")
            content = message.get("content")
            if role not in ("user", "assistant") or not isinstance(content, str):
                continue
            normalized = content.strip()
            if normalized:
                safe.append({"role": role, "content": normalized[:1200]})
        return safe

    @staticmethod
    def _safe_prompt_json(value: dict[str, Any]) -> str:
        serialized = json.dumps(value, ensure_ascii=False, default=str, separators=(",", ":"))
        return serialized[:6000] + ("…" if len(serialized) > 6000 else "")

    def _build_field_deterministic_answer(self, fp: FactPackage, field_report: dict[str, Any]) -> StructuredAnswer:
        facts = fp.facts
        used_fact_ids = [key for key in (
            "field.name", "field.crop.name", "field.score", "field.headline",
            "field.headlineDescription", "field.alert.1.title", "field.alert.1.description",
            "field.task.1.title", "field.task.1.description",
        ) if facts.get(key) not in (None, "")]
        field_name = facts.get("field.name", "내 밭")
        crop_name = facts.get("field.crop.name", "작물")
        headline = facts.get("field.headline") or facts.get("field.alert.1.title")
        description = facts.get("field.headlineDescription") or facts.get("field.alert.1.description")
        task = facts.get("field.task.1.title")
        task_description = facts.get("field.task.1.description")
        parts = [f"{field_name}의 {crop_name} 일일 분석 기준입니다."]
        if headline:
            parts.append(str(headline))
        if description:
            parts.append(str(description))
        if task:
            parts.append(f"우선 할 일: {task}" + (f" — {task_description}" if task_description else ""))
        if not headline and not task:
            parts.append("오늘 저장된 현장 안내를 찾지 못했습니다. 최신 대시보드를 새로고침한 뒤 다시 확인해 주세요.")
        return StructuredAnswer(
            answer="\n".join(parts), basisType="CURRENT_REPORT", usedFactIds=used_fact_ids,
            usedSourceIds=[s.get("sourceId", "") for s in fp.sources if isinstance(s, dict) and s.get("sourceId")],
            mentionedCrops=[str(crop_name)] if crop_name in {profile["name"] for profile in CROP_PROFILES.values()} else [],
        )

    def _build_deterministic_answer(
        self, fp: FactPackage, intent: str, region_analysis: dict[str, Any], tool_results: dict[str, Any]
    ) -> StructuredAnswer:
        facts = fp.facts
        used_fact_ids: list[str] = []
        used_source_ids = [s.get("sourceId", "") for s in fp.sources if isinstance(s, dict)]
        mentioned_numbers: list[float] = []
        mentioned_crops: list[str] = []
        mentioned_risks: list[str] = []
        safety_notice: str | None = None

        region_name = facts.get("region.name", "")
        region_score = facts.get("region.score")
        region_grade = facts.get("region.grade", "")

        if self._is_watering_question(fp.question):
            crop_profile = tool_results.get("crop_profile")
            parts = ["실제 토양수분 센서값이 없어 물주기 필요 여부를 확정할 수 없습니다."]
            parts.append("최근 강수와 예보를 확인하고 뿌리 주변 흙의 수분 상태를 직접 확인하세요.")
            if crop_profile and isinstance(crop_profile, dict):
                watering = crop_profile.get("watering", "")
                if watering:
                    parts.append(f"\n💧 {crop_profile.get('name', '')} 관수 참고: {watering}")
                    mentioned_crops.append(str(crop_profile.get("name", "")))
            used_fact_ids = [k for k in facts if k.startswith("region.")]
            safety_notice = "실제 토양수분 센서값이 없어 물주기 필요 여부를 확정할 수 없습니다."
            return StructuredAnswer(
                answer=" ".join(parts), basisType="CURRENT_REPORT",
                usedFactIds=used_fact_ids, usedSourceIds=used_source_ids,
                mentionedNumbers=[], mentionedCrops=mentioned_crops,
                mentionedRisks=[], safetyNotice=safety_notice,
            )

        if intent == "RISK_EXPLANATION":
            parts = []
            if region_name:
                parts.append(f"📍 {region_name} 분석 기준입니다.")
                used_fact_ids.append("region.name")
            risk_title = facts.get("risk.1.title", "")
            risk_code = facts.get("risk.1.code", "")
            risk_guide = tool_results.get("risk_guide")
            if risk_title:
                parts.append(f"가장 큰 위험은 '{risk_title}'입니다.")
                used_fact_ids.append("risk.1.title")
                mentioned_risks.append(str(risk_title))
                if risk_guide and isinstance(risk_guide, dict):
                    parts.append(f"\n⚠️ {risk_guide.get('title', '')} 상세: {risk_guide.get('detail', '')}")
                    parts.append(f"\n✅ 권장 대응: {risk_guide.get('action', '')}")
            else:
                parts.append("현재 기상 예보에서 확인된 주요 위험은 없습니다. 🟢")
                parts.append("다만, 일기예보는 수시로 변동되므로 정기적으로 확인하세요.")
            risk_action = facts.get("risk.1.action.1", "")
            if risk_action and not risk_guide:
                parts.append(f"권장 행동: {risk_action}")
                used_fact_ids.append("risk.1.action.1")
            answer_text = "\n".join(parts)

        elif intent == "CROP_RECOMMENDATION":
            parts = []
            if region_name:
                parts.append(f"📍 {region_name} 기준 추천 작물입니다.")
                used_fact_ids.append("region.name")
            crop_parts = []
            for i in range(1, 4):
                name = facts.get(f"crop.{i}.name")
                score = facts.get(f"crop.{i}.score")
                if name:
                    profile = get_crop_profile(str(name))
                    emoji = profile.get("emoji", "") if profile else ""
                    label = f"{emoji} {name}"
                    if score is not None:
                        label += f" ({score}점)"
                        mentioned_numbers.append(float(score))
                        used_fact_ids.append(f"crop.{i}.score")
                    crop_parts.append(label)
                    used_fact_ids.append(f"crop.{i}.name")
                    mentioned_crops.append(str(name))
            if crop_parts:
                parts.append("추천 순위: " + " > ".join(crop_parts))
            if region_score is not None:
                parts.append(f"지역 종합 점수: {region_score}점 ({region_grade})")
                used_fact_ids.append("region.score")
                mentioned_numbers.append(float(region_score))
            top_crop = facts.get("crop.1.name")
            if top_crop:
                profile = get_crop_profile(str(top_crop))
                if profile:
                    parts.append(f"\n🌱 {profile['name']} 재배 정보:")
                    parts.append(f"  · 생육 적온: {profile['temp_optimal']}")
                    parts.append(f"  · 적정 pH: {profile['ph_optimal']}")
                    parts.append(f"  · 파종/정식: {profile['planting']}")
                    parts.append(f"  · 수확: {profile['harvest']}")
            answer_text = "\n".join(parts) if parts else "현재 리포트에 추천 작물 데이터가 없습니다."

        elif intent == "CROP_COMPARISON":
            comparison = tool_results.get("crop_comparison", [])
            parts = []
            if region_name:
                parts.append(f"📍 {region_name} 기준 작물 비교입니다.")
                used_fact_ids.append("region.name")
            if comparison:
                parts.append(f"{'작물':<6} {'점수':<6} {'적온':<12} {'적정pH':<10} {'파종시기'}")
                parts.append("─" * 55)
                for c in comparison:
                    score_str = f"{c['score']}점" if c.get("score") is not None else "-"
                    temp = c.get("temp_optimal", "-") or "-"
                    ph = c.get("ph_optimal", "-") or "-"
                    planting = c.get("planting", "-") or "-"
                    parts.append(f"{c['name']:<6} {score_str:<6} {temp:<12} {ph:<10} {planting}")
                    used_fact_ids.append(f"crop.{c['rank']}.name")
                    mentioned_crops.append(str(c["name"]))
                    if c.get("score") is not None:
                        mentioned_numbers.append(float(c["score"]))
            else:
                parts.append("비교할 작물 데이터가 없습니다.")
            answer_text = "\n".join(parts)

        elif intent == "SEASONAL_ADVICE":
            seasonal = tool_results.get("seasonal_advice")
            parts = []
            if seasonal and isinstance(seasonal, dict) and seasonal.get("advice"):
                crop_name = seasonal.get("crop", "")
                month = seasonal.get("month", 0)
                parts.append(f"🗓️ {month}월 {crop_name} 농사 일정:")
                parts.append(f"  {seasonal['advice']}")
                parts.append(f"\n🌡️ 생육 적온: {seasonal.get('temp_optimal', '-')}")
                parts.append(f"💧 관수: {seasonal.get('watering', '-')}")
                mentioned_crops.append(crop_name)
                used_fact_ids.append("crop.1.name")
            elif region_name:
                parts.append(f"📍 {region_name}의 현재 분석 데이터입니다.")
                if region_score is not None:
                    parts.append(f"지역 점수: {region_score}점 ({region_grade})")
                    used_fact_ids.append("region.score")
                    mentioned_numbers.append(float(region_score))
                parts.append("특정 작물을 지정하시면 월별 농사 일정을 안내해 드릴게요.")
            else:
                parts.append("지역 분석을 먼저 완료하시면 계절별 농사 일정을 안내해 드릴게요.")
            answer_text = "\n".join(parts)

        elif intent == "SOIL_ADVICE":
            parts = []
            crop_profile = tool_results.get("crop_profile")
            if region_name:
                parts.append(f"📍 {region_name} 토양 정보입니다.")
                used_fact_ids.append("region.name")
            if crop_profile and isinstance(crop_profile, dict):
                parts.append(f"🌱 {crop_profile.get('name', '')} 토양 요건:")
                parts.append(f"  · 적정 pH: {crop_profile.get('ph_optimal', '-')}")
                parts.append(f"  · 토양: {crop_profile.get('soil_note', '-')}")
                mentioned_crops.append(str(crop_profile.get("name", "")))
            if region_score is not None:
                parts.append(f"\n현재 지역 토양·환경 종합 점수: {region_score}점")
                used_fact_ids.append("region.score")
                mentioned_numbers.append(float(region_score))
            soil_ph = facts.get("component.soil.soilPh")
            soil_ec = facts.get("component.soil.soilEc")
            if soil_ph is not None:
                parts.append(f"측정된 토양 산도(pH): {soil_ph}")
                used_fact_ids.append("component.soil.soilPh")
                mentioned_numbers.append(float(soil_ph))
            if soil_ec is not None:
                parts.append(f"염류 농도(EC): {soil_ec}")
                used_fact_ids.append("component.soil.soilEc")
                mentioned_numbers.append(float(soil_ec))
            if facts.get("data.missing.1"):
                parts.append("일부 토양 자료가 비어 있어 현장 토양검정 결과로 한 번 더 확인해 주세요.")
                used_fact_ids.append("data.missing.1")
            if not parts:
                parts.append("토양 관련 데이터를 확인하려면 지역 분석을 먼저 완료해 주세요.")
            answer_text = "\n".join(parts)

        elif intent == "REPORT_REASON":
            parts = []
            if region_name:
                parts.append(f"📍 {region_name}의 분석 결과입니다.")
                used_fact_ids.append("region.name")
            if region_score is not None:
                parts.append(f"지역 종합 점수: {region_score}점 ({region_grade})")
                used_fact_ids.append("region.score")
                mentioned_numbers.append(float(region_score))
            crop1 = facts.get("crop.1.name")
            crop1_score = facts.get("crop.1.score")
            if crop1:
                label = f"최우선 추천 작물: {crop1}"
                if crop1_score is not None:
                    label += f" ({crop1_score}점)"
                    mentioned_numbers.append(float(crop1_score))
                parts.append(label)
                used_fact_ids.append("crop.1.name")
                mentioned_crops.append(str(crop1))
            crop_reason = facts.get("crop.1.reason.1")
            if crop_reason:
                parts.append(f"추천 근거: {crop_reason}")
                used_fact_ids.append("crop.1.reason.1")
            component_labels = (
                ("기후", "component.climate.score"),
                ("토양", "component.soil.score"),
                ("예보 위험 안전", "component.hazard.safetyScore"),
            )
            component_parts = []
            for label, key in component_labels:
                value = facts.get(key)
                if value is not None:
                    component_parts.append(f"{label} {value}점")
                    used_fact_ids.append(key)
                    mentioned_numbers.append(float(value))
            if component_parts:
                parts.append("점수 구성: " + ", ".join(component_parts))
            risk_title = facts.get("risk.1.title")
            if risk_title:
                parts.append(f"주요 위험: {risk_title}")
                used_fact_ids.append("risk.1.title")
                mentioned_risks.append(str(risk_title))
            else:
                parts.append("현재 예보에서 확인된 주요 위험은 없습니다.")
            confidence_message = facts.get("data.confidence.message")
            if confidence_message:
                parts.append(f"데이터 신뢰도: {confidence_message}")
                used_fact_ids.append("data.confidence.message")
            answer_text = "\n".join(parts) if parts else "리포트의 세부 근거를 확인해 주세요."

        elif intent == "OFFICIAL_GUIDANCE":
            guidance = tool_results.get("official_guidance", [])
            tips = [g for g in guidance if g.get("type") == "tip"]
            if tips:
                answer_text = "📋 " + tips[0]["content"]
                used_fact_ids.append(tips[0].get("factKey", ""))
            else:
                answer_text = "현재 리포트에 저장된 공식 자료가 없습니다.\n농사로(nongsaro.go.kr)에서 작물별 재배 기술을 직접 확인하실 수 있습니다."

        else:
            parts = []
            if region_name:
                parts.append(f"📍 {region_name} 분석 결과입니다.")
                used_fact_ids.append("region.name")
            if region_score is not None:
                parts.append(f"지역 점수: {region_score}점 ({region_grade})")
                used_fact_ids.append("region.score")
                mentioned_numbers.append(float(region_score))
            crop1 = facts.get("crop.1.name")
            if crop1:
                parts.append(f"추천 1순위: {crop1}")
                used_fact_ids.append("crop.1.name")
                mentioned_crops.append(str(crop1))
            risk_title = facts.get("risk.1.title")
            if risk_title:
                parts.append(f"주요 위험: {risk_title}")
                used_fact_ids.append("risk.1.title")
                mentioned_risks.append(str(risk_title))
            else:
                parts.append("현재 확인된 주요 위험은 없습니다.")
            parts.append("\n💡 궁금한 점: 작물 추천 이유, 위험 대응법, 계절별 농사 일정, 용어 설명 등을 물어보세요!")
            answer_text = "\n".join(parts) if parts else "완료된 분석 리포트를 확인한 뒤 다시 질문해 주세요."

        return StructuredAnswer(
            answer=answer_text,
            basisType="CURRENT_REPORT",
            usedFactIds=[f for f in used_fact_ids if f],
            usedSourceIds=used_source_ids,
            mentionedNumbers=mentioned_numbers,
            mentionedCrops=mentioned_crops,
            mentionedRisks=mentioned_risks,
            safetyNotice=safety_notice,
        )

    @staticmethod
    def _is_watering_question(question: str) -> bool:
        lowered = question.replace(" ", "")
        return any(w in lowered for w in ("물주기", "물줘", "물주", "급수", "관수"))

    def _validate_facts_and_sources(self, state: AgentState) -> dict[str, Any]:
        """Grounding check for the LLM answer. Deliberately narrow: it exists
        to catch genuine hallucination (a fact/source id that was never sent,
        or a crop outside our fixed 5-crop catalog), not to demand the LLM's
        self-reported numbers/risk-title strings match a FactPackage value
        character-for-character -- natural paraphrasing of a real number
        (rounding, unit wording, "이번 주" vs a literal date) is not
        hallucination, and rejecting it just to fall back to a duller answer
        made the assistant look like it ignores every question."""
        answer = state.get("structured_answer")
        if answer is None:
            return {"validation_passed": False, "trace": [*state["trace"], "답변 없음 — 검증 실패"]}

        fp = state["fact_package"]
        valid_fact_keys = set(fp.facts.keys())
        valid_source_ids = {s.get("sourceId", "") for s in fp.sources if isinstance(s, dict)}
        known_crop_names = {profile["name"] for profile in CROP_PROFILES.values()}

        errors: list[str] = []
        for fid in answer.usedFactIds:
            if fid and fid not in valid_fact_keys:
                errors.append(f"Fact '{fid}' not in FactPackage")
        for sid in answer.usedSourceIds:
            if sid and sid not in valid_source_ids:
                errors.append(f"Source '{sid}' not in FactPackage")
        for crop in answer.mentionedCrops:
            if crop and crop not in known_crop_names:
                errors.append(f"Crop '{crop}' is not one of the 5 supported crops")

        if answer.basisType in ("CURRENT_REPORT", "OFFICIAL_GUIDANCE") and not answer.usedSourceIds:
            if fp.sources:
                errors.append("basisType requires sources but usedSourceIds is empty")

        passed = len(errors) == 0
        trace_msg = "Fact/Source 검증 통과" if passed else f"검증 실패: {errors}"
        return {"validation_passed": passed, "trace": [*state["trace"], trace_msg]}

    def _fallback_or_return(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        if state.get("validation_passed") and state.get("structured_answer"):
            return {
                "final_answer": state["structured_answer"],
                "final_sources": fp.sources,
                "trace": [*state["trace"], "검증된 답변 반환"],
            }

        # The LLM answer either wasn't attempted or failed grounding
        # validation. Use the intent-specific, fact-only answer computed
        # earlier in _compose_structured_answer -- it is guaranteed to pass
        # validation (built directly from facts) and, critically, still
        # differs by intent (risk explanation vs crop recommendation vs
        # watering guidance, etc.), so a rejected LLM answer never collapses
        # every question into the same generic summary.
        deterministic = state.get("deterministic_answer")
        if deterministic is not None:
            return {
                "final_answer": deterministic,
                "final_sources": fp.sources,
                "trace": [*state["trace"], "LLM 검증 실패 — 규칙 기반 답변으로 대체"],
            }

        last_resort = StructuredAnswer(
            answer="완료된 분석 리포트를 확인한 뒤 다시 질문해 주세요.",
            basisType="CURRENT_REPORT",
        )
        return {
            "final_answer": last_resort,
            "final_sources": fp.sources,
            "trace": [*state["trace"], "리포트 없음 — 최종 안내 반환"],
        }

    @staticmethod
    def _chunks(value: str, width: int) -> Iterable[str]:
        return (value[i : i + width] for i in range(0, len(value), width))


ai_service = AIService()
