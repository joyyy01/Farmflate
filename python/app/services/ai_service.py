from __future__ import annotations

import json
import re
from typing import Any, AsyncGenerator, Iterable
from uuid import uuid4

import httpx
from langgraph.graph import END, START, StateGraph
from typing_extensions import TypedDict

from app.core.config import settings
from app.rag.retriever import rag_retriever
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
from app.services.screen_tools import (
    TargetResolution,
    compare_visible_crops,
    explain_visible_metric,
    recommend_next_checks,
    resolve_visible_target,
    summarize_report_evidence,
)


class AgentState(TypedDict, total=False):
    fact_package: FactPackage
    intent: str
    conversation_focus: str
    target_resolution: TargetResolution
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
    _WATER_WORDS = ("물주기", "물줘", "물 줘", "급수", "관수", "물주", "물 주", "물은", "물을")
    _SEASON_WORDS = ("계절", "시기", "달", "월", "언제", "파종", "정식", "수확", "전정", "가지치기", "심는", "거두", "심어야", "심어")
    _COMPARE_WORDS = ("비교", "차이", "vs", "어느", "골라", "선택", "낫")
    _SOIL_WORDS = ("토양", "흙", "산도", "비료", "퇴비", "석회", "양분", "거름")

    def __init__(self) -> None:
        builder = StateGraph(AgentState)
        builder.add_node("validate_request", self._validate_request)
        builder.add_node("resolve_visible_target", self._resolve_visible_target)
        builder.add_node("classify_intent", self._classify_intent)
        builder.add_node("select_tools", self._select_tools)
        builder.add_node("execute_tools", self._execute_tools)
        builder.add_node("compose_structured_answer", self._compose_structured_answer)
        builder.add_node("validate_facts_and_sources", self._validate_facts_and_sources)
        builder.add_node("fallback_or_return", self._fallback_or_return)
        builder.add_edge(START, "validate_request")
        builder.add_edge("validate_request", "resolve_visible_target")
        builder.add_edge("resolve_visible_target", "classify_intent")
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
            title=s.get("title") or (s.get("provider", "") + " " + s.get("service", "")).strip(),
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
        raw_tasks = facts.get("tasks") if isinstance(facts.get("tasks"), list) else facts.get("candidateTasks", [])
        tasks: list[FieldGuidanceTask] = []
        for raw_task in raw_tasks:
            if not isinstance(raw_task, dict):
                continue
            key = str(raw_task.get("key") or "").strip()
            title = str(raw_task.get("title") or "").strip()
            description = str(raw_task.get("description") or "").strip()
            if key and title and description:
                tasks.append(FieldGuidanceTask(key=key, title=title[:120], description=description[:300]))

        raw_alerts = facts.get("alerts") if isinstance(facts.get("alerts"), list) else facts.get("candidateAlerts", [])
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
        reasoning_summary = self._build_field_guidance_reasoning(facts, crop_name, tasks, alert_titles)
        if settings.OPENAI_API_KEY and settings.LLM_PROVIDER.lower() == "openai":
            llm_summary = await self._call_field_guidance_reasoning(facts, crop_name, reasoning_summary)
            if llm_summary is not None:
                reasoning_summary = llm_summary
        return FieldGuidanceResponse(
            headline=headline,
            headlineDescription=headline_description,
            tasks=tasks,
            reasoningSummary=reasoning_summary,
        )

    @staticmethod
    def _build_field_guidance_reasoning(
        facts: dict[str, Any], crop_name: str, tasks: list[FieldGuidanceTask], alert_titles: list[str]
    ) -> str:
        reasoning_points = facts.get("reasoningPoints") if isinstance(facts.get("reasoningPoints"), list) else []
        reason = next((str(point).strip() for point in reasoning_points if str(point).strip()), "")
        alert = alert_titles[0] if alert_titles else "오늘의 환경 변화"
        action = tasks[0].title if tasks else "밭 상태 확인"
        if reason:
            return f"{crop_name}에 {alert}가 예상돼요. {reason} 그래서 {action}을 먼저 안내했어요."[:500]
        return f"{crop_name}의 오늘 환경을 확인한 결과 {alert}에 대비해 {action}을 먼저 안내했어요."[:500]

    async def _call_field_guidance_reasoning(
        self, facts: dict[str, Any], crop_name: str, fallback: str
    ) -> str | None:
        safe_facts = self._safe_prompt_json(facts)
        system_prompt = (
            "당신은 초보 농업인을 위한 Farmflate 안내문 작성기입니다. 제공된 사실만 사용해 '왜 이렇게 안내했나요?' "
            "요약을 한국어 한두 문장으로 작성하세요. 작물, 경고, 작업, 수치는 제공된 사실 밖으로 만들지 마세요. "
            "원시 숫자만 나열하지 말고 조건-영향-확인 행동을 연결하세요. 반드시 JSON만 반환하세요: "
            '{"reasoningSummary":"요약"}'
        )
        try:
            async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
                response = await client.post(
                    f"{settings.OPENAI_BASE_URL.rstrip('/')}/chat/completions",
                    headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                    json={
                        "model": settings.OPENAI_MODEL,
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": f"검증된 사실: {safe_facts}\n기본 안전 요약: {fallback}"},
                        ],
                        "temperature": 0.2,
                    },
                )
                response.raise_for_status()
                content = response.json()["choices"][0]["message"]["content"].strip()
                if content.startswith("```"):
                    content = re.sub(r"^```(?:json)?\s*", "", content)
                    content = re.sub(r"\s*```$", "", content)
                summary = str(json.loads(content).get("reasoningSummary", "")).strip()
                if not self._is_valid_field_guidance_summary(summary, facts, crop_name):
                    return None
                return summary
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            return None

    @staticmethod
    def _is_valid_field_guidance_summary(summary: str, facts: dict[str, Any], crop_name: str) -> bool:
        if not summary or len(summary) > 500 or crop_name not in summary:
            return False
        known_numbers = {match for match in re.findall(r"\d+(?:\.\d+)?", json.dumps(facts, ensure_ascii=False, default=str))}
        mentioned_numbers = set(re.findall(r"\d+(?:\.\d+)?", summary))
        if not mentioned_numbers.issubset(known_numbers):
            return False
        task_titles = {
            str(item.get("title", ""))
            for key in ("tasks", "candidateTasks")
            for item in (facts.get(key) if isinstance(facts.get(key), list) else [])
            if isinstance(item, dict) and str(item.get("title", ""))
        }
        alert_titles = {
            str(item.get("title", ""))
            for key in ("alerts", "candidateAlerts")
            for item in (facts.get(key) if isinstance(facts.get(key), list) else [])
            if isinstance(item, dict) and str(item.get("title", ""))
        }
        known_risks = {profile["name"] for profile in CROP_PROFILES.values()}
        mentions_condition = not alert_titles or any(title in summary for title in alert_titles)
        mentions_action = not task_titles or any(title in summary for title in task_titles)
        return (
            not any(risk in summary for risk in known_risks - {crop_name})
            and bool(task_titles or alert_titles)
            and mentions_condition
            and mentions_action
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

    def _resolve_visible_target(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        context = fp.context if isinstance(fp.context, dict) else {}
        visible_data = context.get("visibleData", [])
        resolution = resolve_visible_target(fp.question, visible_data, fp.facts, fp.history)
        trace = "화면 대상 없음"
        if resolution.status == "resolved":
            trace = f"화면 대상 확인: {resolution.label}"
        elif resolution.status == "ambiguous":
            trace = "화면 대상이 여러 개라 명확화 필요"
        return {"target_resolution": resolution, "trace": [*state["trace"], trace]}

    def _classify_intent(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        question = fp.question
        target = state.get("target_resolution")
        visible_data = fp.context.get("visibleData", []) if isinstance(fp.context, dict) else []
        if isinstance(target, TargetResolution) and target.status == "ambiguous":
            intent = "SCREEN_CLARIFICATION"
        elif isinstance(target, TargetResolution) and target.status == "resolved":
            lowered = question.replace(" ", "").lower()
            if len(target.fact_keys) > 0 and all(key.startswith("crop.") for key in target.fact_keys) and any(
                token in lowered for token in self._COMPARE_WORDS + ("첫번째", "두번째", "1위", "2위")
            ):
                intent = "COMPARE_VISIBLE_DATA"
            elif any(key.startswith(("risk.", "field.alert.")) for key in target.fact_keys) and any(
                token in lowered for token in self._WHY_WORDS + self._RISK_WORDS
            ):
                intent = "EVIDENCE_REQUEST"
            elif any(key.startswith("field.task.") for key in target.fact_keys):
                intent = "NEXT_CHECK_RECOMMENDATION"
            else:
                intent = "EXPLAIN_VISIBLE_DATA"
        else:
            intent = self._classify(question)
        focus = self._conversation_focus(fp)
        trace = f"의도 분류: {intent}"
        if focus:
            trace += " (직전 대화 맥락 반영)"
        return {
            "intent": intent,
            "conversation_focus": focus,
            "trace": [*state["trace"], trace],
        }

    def _conversation_focus(self, fp: FactPackage) -> str:
        """Keep a compact, fact-backed bridge for Korean follow-up questions.

        The full history is still passed to the LLM, but the deterministic path
        needs a small explicit anchor for questions such as “그 위험은?” or
        “그러면 물은요?”.  Only persisted field/report facts are used here.
        """
        question = fp.question.replace(" ", "")
        follow_up_tokens = ("그위험", "이위험", "그상태", "이상태", "그작물", "이작물", "그러면", "그럼", "그것", "이것")
        if not any(token in question for token in follow_up_tokens):
            return ""

        anchors: list[str] = []
        crop = fp.facts.get("field.crop.name") or fp.facts.get("crop.1.name")
        risk = fp.facts.get("field.alert.1.title") or fp.facts.get("risk.1.title")
        if crop:
            anchors.append(f"현재 작물: {crop}")
        if risk:
            anchors.append(f"현재 위험: {risk}")
        return "; ".join(anchors)

    def _classify(self, message: str) -> str:
        lowered = message.replace(" ", "")
        if any(w in lowered for w in self._UNSUPPORTED_WORDS):
            return "UNSUPPORTED_ACTION"
        # A concrete glossary term (for example pH or EC) is more specific
        # than the broad topic words such as “토양”, so explain it directly.
        if self._contains_known_term(message):
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
        if any(w in lowered for w in self._GUIDANCE_WORDS):
            return "OFFICIAL_GUIDANCE"
        if any(w in lowered for w in self._SOIL_WORDS):
            return "SOIL_ADVICE"
        if any(w in lowered for w in self._RISK_WORDS):
            return "RISK_EXPLANATION"
        if any(w in lowered for w in self._CROP_WORDS):
            return "CROP_RECOMMENDATION"
        # Generic interrogatives such as “무엇” are also listed with the
        # glossary cues.  Evaluate them only after the actionable intents so
        # “오늘 무엇을 조심해야 하나요?” remains a risk question.
        if any(w in lowered for w in self._TERM_WORDS):
            return "TERM_EXPLANATION"
        return "GENERAL_INFORMATION"

    @staticmethod
    def _contains_known_term(message: str) -> bool:
        from app.services.tools import AGRICULTURAL_GLOSSARY
        normalized = message.lower()
        return any(term.lower() in normalized for term in AGRICULTURAL_GLOSSARY)

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
            "OFFICIAL_GUIDANCE": ["search_knowledge", "get_report_sources"],
            "GENERAL_INFORMATION": ["get_region_analysis", "get_report_sources"],
            "EXPLAIN_VISIBLE_DATA": ["explain_visible_metric"],
            "COMPARE_VISIBLE_DATA": ["compare_visible_crops"],
            "EVIDENCE_REQUEST": ["summarize_report_evidence"],
            "NEXT_CHECK_RECOMMENDATION": ["recommend_next_checks"],
            "SCREEN_CLARIFICATION": [],
            "UNSUPPORTED_ACTION": [],
        }
        selected = list(tool_map.get(intent, ["get_region_analysis", "get_report_sources"]))[:2]
        # A field dashboard conversation should have access to the persisted
        # snapshot too. It is scoped by the Spring service before it reaches
        # the agent, so this adds context without granting data access.
        if (not intent.endswith("VISIBLE_DATA") and intent not in {"EVIDENCE_REQUEST", "NEXT_CHECK_RECOMMENDATION", "SCREEN_CLARIFICATION"}
                and any(key.startswith("field.") for key in state["fact_package"].facts) and "get_field_report" not in selected):
            selected.append("get_field_report")
        return {"selected_tools": selected, "trace": [*state["trace"], f"도구 선택: {selected}"]}

    async def _execute_tools(self, state: AgentState) -> dict[str, Any]:
        fp = state["fact_package"]
        target = state.get("target_resolution")
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
            elif tool_name == "search_knowledge":
                retrieval = await rag_retriever.retrieve(fp.question)
                results["knowledge_search"] = retrieval.tool_payload()
                if retrieval.insufficient_evidence:
                    results["official_guidance"] = {"found": False, "reason": "insufficient approved knowledge evidence"}
            elif tool_name == "get_crop_profile":
                crop = self._extract_crop_name(fp.question, fp.facts, fp.history)
                results["crop_profile"] = get_crop_profile(crop) if crop else None
            elif tool_name == "get_seasonal_advice":
                crop = self._extract_crop_name(fp.question, fp.facts, fp.history)
                results["seasonal_advice"] = get_seasonal_advice(crop) if crop else None
            elif tool_name == "get_risk_guide":
                risk_code = fp.facts.get("risk.1.code", "")
                results["risk_guide"] = get_risk_guide(str(risk_code)) if risk_code else None
            elif tool_name == "compare_crops":
                results["crop_comparison"] = compare_crops(fp.facts)
            elif tool_name == "explain_visible_metric" and isinstance(target, TargetResolution):
                results["visible_metric"] = explain_visible_metric(target, fp.facts)
            elif tool_name == "summarize_report_evidence" and isinstance(target, TargetResolution):
                results["visible_evidence"] = summarize_report_evidence(target, fp.facts, fp.sources)
            elif tool_name == "compare_visible_crops" and isinstance(target, TargetResolution):
                visible_data = fp.context.get("visibleData", []) if isinstance(fp.context, dict) else []
                crop_keys = tuple(
                    str(ref.get("key")) for ref in visible_data
                    if isinstance(ref, dict) and str(ref.get("key", "")).startswith("crop.")
                )
                results["visible_crop_comparison"] = compare_visible_crops(crop_keys, fp.facts)
            elif tool_name == "recommend_next_checks" and isinstance(target, TargetResolution):
                results["next_checks"] = recommend_next_checks(target, fp.facts)
        return {"tool_results": results, "trace": [*state["trace"], f"도구 실행: {list(results.keys())}"]}

    def _extract_crop_name(
        self, question: str, facts: dict[str, Any], history: list[dict[str, Any]] | None = None
    ) -> str | None:
        explicit_crop = self._crop_name_in_text(question)
        if explicit_crop:
            return explicit_crop
        # Follow-up questions often omit the crop name. Prefer the user's most
        # recent explicit crop mention over a report's default recommendation.
        for message in reversed(history or []):
            if not isinstance(message, dict) or message.get("role") != "user":
                continue
            content = message.get("content")
            if not isinstance(content, str):
                continue
            history_crop = self._crop_name_in_text(content)
            if history_crop:
                return history_crop
        field_crop = facts.get("field.crop.name")
        if field_crop:
            return str(field_crop)
        top_crop = facts.get("crop.1.name")
        return str(top_crop) if top_crop else None

    @staticmethod
    def _crop_name_in_text(text: str) -> str | None:
        for profile in CROP_PROFILES.values():
            crop_name = profile["name"]
            # “배” is a valid crop but also a syllable in common words such as
            # “재배”. Treat it as a Korean token, rather than a raw substring.
            if crop_name == "배":
                if re.search(r"(?<![가-힣])배(?=$|[\s,./!?]|[은는이가을를와과도만])", text):
                    return crop_name
            elif crop_name in text:
                return crop_name
        return None

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

        if intent == "SCREEN_CLARIFICATION":
            target = state.get("target_resolution")
            clarification = target.clarification if isinstance(target, TargetResolution) else None
            answer = StructuredAnswer(
                answer=clarification or "현재 화면에서 어떤 항목을 설명할지 알려주세요.",
                basisType="GENERAL_INFORMATION",
            )
            return {
                "structured_answer": answer,
                "deterministic_answer": answer,
                "trace": [*state["trace"], "화면 항목 명확화 응답 생성"],
            }

        if intent in {"EXPLAIN_VISIBLE_DATA", "COMPARE_VISIBLE_DATA", "EVIDENCE_REQUEST", "NEXT_CHECK_RECOMMENDATION"}:
            answer = self._build_visible_data_answer(fp, intent, tool_results)
            return {
                "structured_answer": answer,
                "deterministic_answer": answer,
                "trace": [*state["trace"], "읽기 전용 화면 근거 응답 생성"],
            }

        if intent == "TERM_EXPLANATION" and "term_explanation" in tool_results:
            term_result = tool_results["term_explanation"]
            if term_result.get("found"):
                answer = StructuredAnswer(
                    answer=f"{term_result['term']}: {term_result['definition']}",
                    basisType="TERM_DEFINITION",
                )
                return {"structured_answer": answer, "trace": [*state["trace"], "용어 사전 응답 생성"]}

        if intent == "OFFICIAL_GUIDANCE":
            knowledge = tool_results.get("knowledge_search", {})
            evidence = knowledge.get("evidence", []) if isinstance(knowledge, dict) else []
            citations = knowledge.get("citations", []) if isinstance(knowledge, dict) else []
            if not evidence:
                answer = StructuredAnswer(
                    answer="승인된 지식 문서에서 현재 질문을 뒷받침할 근거를 찾지 못했습니다. 지역 또는 작물 정보를 더 구체적으로 알려 주세요.",
                    basisType="GENERAL_INFORMATION",
                )
                return {"structured_answer": answer, "deterministic_answer": answer, "trace": [*state["trace"], "지식 근거 부족"]}
            excerpts = [
                str(item.get("content", "")).strip()[:600]
                for item in evidence[:3]
                if isinstance(item, dict) and str(item.get("content", "")).strip()
            ]
            source_ids = [
                str(item.get("sourceId", ""))
                for item in citations
                if isinstance(item, dict) and str(item.get("sourceId", ""))
            ]
            answer = StructuredAnswer(
                answer="\n\n".join(excerpts),
                basisType="OFFICIAL_GUIDANCE",
                usedSourceIds=list(dict.fromkeys(source_ids)),
            )
            return {"structured_answer": answer, "deterministic_answer": answer, "trace": [*state["trace"], "PostgreSQL 지식 근거 응답 생성"]}

        region_analysis = tool_results.get("region_analysis", {})
        field_report = tool_results.get("field_report", {})
        if not region_analysis and not field_report:
            answer = self._build_context_free_answer(fp, intent, tool_results)
            return {
                "structured_answer": answer,
                "deterministic_answer": answer,
                "trace": [*state["trace"], "분석 전 기본 안내 응답 생성"],
            }

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
            self._build_field_deterministic_answer(fp, field_report, intent)
            if field_report
            else self._build_deterministic_answer(fp, intent, region_analysis, tool_results)
        )

        # A field watering answer must keep the sensor limitation and the
        # direct soil-check action intact.  A prose model can otherwise turn
        # a generic crop profile into an unsafe “water now” instruction.
        if field_report and intent == "WATERING_GUIDANCE":
            return {
                "structured_answer": deterministic_answer,
                "deterministic_answer": deterministic_answer,
                "trace": [*state["trace"], "현장 관수 안전 안내를 규칙 기반으로 고정"],
            }

        if settings.OPENAI_API_KEY and settings.LLM_PROVIDER.lower() == "openai":
            llm_answer = await self._call_openai(fp, intent, tool_results, state.get("conversation_focus", ""))
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
        self, fp: FactPackage, intent: str, tool_results: dict[str, Any], conversation_focus: str = ""
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
            f"질문: {fp.question}\n"
            f"대화 연결 단서: {conversation_focus or '없음'}\n\nFact:\n{facts_text}\n\n"
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

    def _build_context_free_answer(
        self, fp: FactPackage, intent: str, tool_results: dict[str, Any]
    ) -> StructuredAnswer:
        """Answer the starter cards without pretending a personal report exists."""
        crop_profile = tool_results.get("crop_profile")
        if intent == "CROP_RECOMMENDATION" and isinstance(crop_profile, dict):
            crop_name = str(crop_profile.get("name", "작물"))
            parts = [
                f"{crop_profile.get('emoji', '')} {crop_name}의 기본 재배 조건입니다.",
                f"생육에 알맞은 기온은 {crop_profile.get('temp_optimal', '작물별 기준')}이고, 토양 pH는 {crop_profile.get('ph_optimal', '작물별 기준')}를 참고하세요.",
            ]
            if crop_profile.get("soil_note"):
                parts.append(f"토양: {crop_profile['soil_note']}")
            if crop_profile.get("watering"):
                parts.append(f"물 관리: {crop_profile['watering']}")
            return StructuredAnswer(
                answer="\n".join(parts),
                basisType="GENERAL_INFORMATION",
                mentionedCrops=[crop_name] if crop_name in {profile["name"] for profile in CROP_PROFILES.values()} else [],
            )

        if intent == "TERM_EXPLANATION":
            return StructuredAnswer(
                answer="궁금한 용어를 함께 적어 주세요. 예를 들어 ‘토양 pH는 무슨 뜻인가요?’ 또는 ‘EC가 높다는 것은 무엇인가요?’처럼 물어보면 쉬운 말로 설명해 드릴게요.",
                basisType="TERM_DEFINITION",
            )

        normalized_question = fp.question.replace(" ", "")
        if "지역분석" in normalized_question or "분석에서" in normalized_question:
            return StructuredAnswer(
                answer="지역 분석에서는 최근 기온·강수·습도 추이와 예보, 토양 pH·EC 같은 환경 정보를 작물별 기준과 비교합니다. 범위를 벗어난 정도와 며칠간 이어지는 위험 신호를 함께 반영해 점수와 우선 확인 항목을 안내해요.",
                basisType="GENERAL_INFORMATION",
            )

        return StructuredAnswer(
            answer="아직 내 지역 또는 밭 분석 결과가 없어 개인화된 판단은 할 수 없습니다. 다만 작물의 기본 재배 조건, 토양 pH·EC 같은 용어 설명, 지역 분석 방식은 바로 안내해 드릴 수 있어요.",
            basisType="GENERAL_INFORMATION",
        )

    @staticmethod
    def _topic_particle(value: str) -> str:
        """Return the Korean topic particle matching the final Hangul syllable."""
        if not value:
            return "은"
        codepoint = ord(value[-1])
        if 0xAC00 <= codepoint <= 0xD7A3:
            return "은" if (codepoint - 0xAC00) % 28 else "는"
        return "은"

    def _build_visible_data_answer(
        self, fp: FactPackage, intent: str, tool_results: dict[str, Any]
    ) -> StructuredAnswer:
        source_ids = [s.get("sourceId", "") for s in fp.sources if isinstance(s, dict) and s.get("sourceId")]

        if intent == "COMPARE_VISIBLE_DATA":
            comparison = tool_results.get("visible_crop_comparison", {})
            crops = comparison.get("crops", []) if isinstance(comparison, dict) else []
            used = comparison.get("used_fact_ids", []) if isinstance(comparison, dict) else []
            if len(crops) >= 2:
                first, second = crops[0], crops[1]
                first_score = first.get("score")
                second_score = second.get("score")
                score_text = (
                    f"화면 기준 {first['name']}{self._topic_particle(str(first['name']))} {first_score}점, "
                    f"{second['name']}{self._topic_particle(str(second['name']))} {second_score}점으로 표시돼 있어요."
                    if first_score is not None and second_score is not None
                    else f"화면에서 {first['name']}과 {second['name']}을 비교할 수 있어요."
                )
                return StructuredAnswer(
                    answer=score_text + " 점수 외에도 각 작물 카드의 장점과 주의 사항을 함께 확인해 주세요.",
                    basisType="CURRENT_REPORT", usedFactIds=list(used), usedSourceIds=source_ids,
                    mentionedCrops=[str(first["name"]), str(second["name"])],
                )
            return StructuredAnswer(
                answer="현재 화면에서 비교할 두 작물 정보를 찾지 못했어요. 비교할 작물 카드를 선택해 다시 물어봐 주세요.",
                basisType="GENERAL_INFORMATION",
            )

        if intent == "EVIDENCE_REQUEST":
            evidence = tool_results.get("visible_evidence", {})
            values = evidence.get("facts", {}) if isinstance(evidence, dict) else {}
            used = evidence.get("used_fact_ids", []) if isinstance(evidence, dict) else []
            label = evidence.get("label", "이 안내") if isinstance(evidence, dict) else "이 안내"
            title = values.get("field.alert.1.title") or values.get("risk.1.title") or label
            detail = values.get("field.alert.1.description") or values.get("risk.1.action.1")
            weather = [
                f"최고 {values['field.weather.maxTemperature']}℃" for key in ["field.weather.maxTemperature"] if key in values
            ] + [
                f"최저 {values['field.weather.minTemperature']}℃" for key in ["field.weather.minTemperature"] if key in values
            ]
            parts = [f"‘{title}’ 안내는 현재 화면의 검증된 분석 근거를 바탕으로 한 것입니다."]
            if detail:
                parts.append(str(detail))
            if weather:
                parts.append("함께 반영된 날씨는 " + ", ".join(weather) + "입니다.")
            return StructuredAnswer(
                answer=" ".join(parts), basisType="CURRENT_REPORT", usedFactIds=list(used), usedSourceIds=source_ids,
                mentionedRisks=[str(title)],
            )

        if intent == "NEXT_CHECK_RECOMMENDATION":
            checks = tool_results.get("next_checks", {})
            values = checks.get("facts", {}) if isinstance(checks, dict) else {}
            used = checks.get("used_fact_ids", []) if isinstance(checks, dict) else []
            task = values.get("field.task.1.title") or values.get("risk.1.action.1")
            description = values.get("field.task.1.description")
            if task:
                text = f"현재 화면에서는 ‘{task}’를 먼저 확인하는 것이 좋아요."
                if description:
                    text += f" {description}"
            else:
                text = "현재 화면에서 먼저 확인할 관리 항목을 찾지 못했어요. 최신 분석을 다시 확인해 주세요."
            return StructuredAnswer(
                answer=text, basisType="CURRENT_REPORT", usedFactIds=list(used), usedSourceIds=source_ids,
            )

        metric = tool_results.get("visible_metric", {})
        values = metric.get("facts", {}) if isinstance(metric, dict) else {}
        used = metric.get("used_fact_ids", []) if isinstance(metric, dict) else []
        label = metric.get("label", "이 항목") if isinstance(metric, dict) else "이 항목"
        if "field.reasoning.1" in values:
            crop = values.get("field.crop.name", "이 작물")
            alert = values.get("field.alert.1.title", "오늘의 환경 변화")
            task = values.get("field.task.1.title", "밭 상태 확인")
            text = f"{crop}에 {alert}가 확인돼 {task}을 먼저 안내했어요."
        elif "component.soil.soilPh" in values or "field.soil.ph" in values:
            value = values.get("component.soil.soilPh", values.get("field.soil.ph"))
            text = f"{label}는 현재 화면에서 {value}로 표시돼 있어요. pH는 흙의 산성·알칼리성 정도를 뜻하며, 작물이 잘 자라는 범위와 얼마나 가까운지 확인하는 데 사용해요."
        elif "component.soil.soilEc" in values or "field.soil.ec" in values:
            value = values.get("component.soil.soilEc", values.get("field.soil.ec"))
            text = f"{label}는 현재 화면에서 {value}로 표시돼 있어요. EC는 흙 속에 녹은 양분 농도를 간접적으로 보여줘서, 너무 높거나 낮은지 확인하는 지표예요."
        else:
            fact_text = ", ".join(f"{key.split('.')[-1]} {value}" for key, value in values.items())
            text = f"{label}는 현재 화면의 검증된 분석값({fact_text})을 바탕으로 표시된 항목이에요." if fact_text else f"{label}의 세부 분석값을 찾지 못했어요."
        return StructuredAnswer(
            answer=text, basisType="CURRENT_REPORT", usedFactIds=list(used), usedSourceIds=source_ids,
        )

    def _build_field_deterministic_answer(
        self, fp: FactPackage, field_report: dict[str, Any], intent: str
    ) -> StructuredAnswer:
        facts = fp.facts
        used_fact_ids = [key for key in (
            "field.name", "field.crop.name", "field.score", "field.headline",
            "field.headlineDescription", "field.alert.1.title", "field.alert.1.description",
            "field.task.1.title", "field.task.1.description", "field.reasoning.1",
            "field.weather.minTemperature", "field.weather.maxTemperature", "field.weather.humidity",
            "field.weather.rainfall",
        ) if facts.get(key) not in (None, "")]
        field_name = facts.get("field.name", "내 밭")
        crop_name = facts.get("field.crop.name", "작물")
        headline = facts.get("field.headline") or facts.get("field.alert.1.title")
        description = facts.get("field.headlineDescription") or facts.get("field.alert.1.description")
        task = facts.get("field.task.1.title")
        task_description = facts.get("field.task.1.description")
        alert_title = facts.get("field.alert.1.title")
        alert_description = facts.get("field.alert.1.description")
        reasoning = facts.get("field.reasoning.1")
        weather_parts = []
        if facts.get("field.weather.minTemperature") not in (None, ""):
            weather_parts.append(f"최저 {facts['field.weather.minTemperature']}℃")
        if facts.get("field.weather.maxTemperature") not in (None, ""):
            weather_parts.append(f"최고 {facts['field.weather.maxTemperature']}℃")
        if facts.get("field.weather.humidity") not in (None, ""):
            weather_parts.append(f"습도 {facts['field.weather.humidity']}%")
        if facts.get("field.weather.rainfall") not in (None, ""):
            weather_parts.append(f"강수 {facts['field.weather.rainfall']}mm")

        parts = [f"{field_name}의 {crop_name} 오늘 분석 기준입니다."]
        safety_notice: str | None = None
        mentioned_risks = [str(alert_title)] if alert_title else []

        if intent == "WATERING_GUIDANCE":
            parts.append("실제 토양수분 센서값이 없어서 지금 바로 물을 줘야 한다고 단정할 수는 없습니다.")
            if weather_parts:
                parts.append("오늘 확인된 환경은 " + ", ".join(weather_parts) + "입니다.")
            parts.append("물을 주기 전에는 뿌리 주변 3~5cm 흙을 직접 만져 수분을 확인하고, 배수 상태와 잎 처짐을 함께 살펴보세요.")
            if task:
                parts.append(f"오늘의 관리 우선순위: {task}" + (f" — {task_description}" if task_description else ""))
            safety_notice = "실제 토양수분 센서값이 없어 물주기 필요 여부를 확정할 수 없습니다."
        elif intent == "REPORT_REASON":
            if headline:
                parts.append(f"현재 안내는 ‘{headline}’ 때문입니다.")
            if description:
                parts.append(str(description))
            if reasoning:
                parts.append(f"분석 근거: {reasoning}")
            if weather_parts:
                parts.append("함께 반영된 오늘 환경은 " + ", ".join(weather_parts) + "입니다.")
            if task:
                parts.append(f"그래서 먼저 할 일은 {task}" + (f"입니다. {task_description}" if task_description else "입니다."))
        elif intent == "RISK_EXPLANATION":
            if alert_title:
                parts.append(f"오늘 가장 먼저 살필 위험은 ‘{alert_title}’입니다.")
            if alert_description:
                parts.append(str(alert_description))
            elif description:
                parts.append(str(description))
            if weather_parts:
                parts.append("관련 환경은 " + ", ".join(weather_parts) + "입니다.")
            if task:
                parts.append(f"대응은 {task}" + (f" — {task_description}" if task_description else "부터 해주세요."))
        else:
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
            mentionedRisks=mentioned_risks,
            safetyNotice=safety_notice,
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
        knowledge = state.get("tool_results", {}).get("knowledge_search", {})
        valid_source_ids.update(
            citation.get("sourceId", "")
            for citation in knowledge.get("citations", [])
            if isinstance(citation, dict)
        )
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
        knowledge_sources = state.get("tool_results", {}).get("knowledge_search", {}).get("citations", [])
        final_sources = [*fp.sources, *[source for source in knowledge_sources if isinstance(source, dict)]]
        if state.get("validation_passed") and state.get("structured_answer"):
            return {
                "final_answer": state["structured_answer"],
                "final_sources": final_sources,
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
                "final_sources": final_sources,
                "trace": [*state["trace"], "LLM 검증 실패 — 규칙 기반 답변으로 대체"],
            }

        last_resort = StructuredAnswer(
            answer="완료된 분석 리포트를 확인한 뒤 다시 질문해 주세요.",
            basisType="CURRENT_REPORT",
        )
        return {
            "final_answer": last_resort,
            "final_sources": final_sources,
            "trace": [*state["trace"], "리포트 없음 — 최종 안내 반환"],
        }

    @staticmethod
    def _chunks(value: str, width: int) -> Iterable[str]:
        return (value[i : i + width] for i in range(0, len(value), width))


ai_service = AIService()
