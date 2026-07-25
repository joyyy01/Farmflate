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
)


class AgentState(TypedDict, total=False):
    fact_package: FactPackage
    intent: str
    selected_tools: list[str]
    tool_results: dict[str, Any]
    structured_answer: StructuredAnswer
    validation_passed: bool
    final_answer: StructuredAnswer
    final_sources: list[dict[str, Any]]
    trace: list[str]


class AIService:
    _RISK_WORDS = ("위험", "주의", "조심", "병", "피해", "재해", "호우", "폭염", "강풍", "저온")
    _CROP_WORDS = ("작물", "심", "재배", "추천", "감자", "배", "오이", "상추", "사과", "적합")
    _WHY_WORDS = ("왜", "이유", "근거", "분석", "점수", "어떻게")
    _TERM_WORDS = ("뜻", "의미", "뭐", "무엇", "용어", "EC", "pH", "유기물", "질소", "인산", "칼륨")
    _GUIDANCE_WORDS = ("농사로", "공식", "가이드", "관리법", "배수", "안내", "지침")
    _UNSUPPORTED_WORDS = ("바꿔", "변경", "수정", "조정해", "높여", "낮춰", "삭제", "취소")
    _WATER_WORDS = ("물", "급수", "관수", "물주기")

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
        if any(w in lowered for w in self._GUIDANCE_WORDS):
            return "OFFICIAL_GUIDANCE"
        if any(w in lowered for w in self._RISK_WORDS):
            return "RISK_EXPLANATION"
        if any(w in lowered for w in self._CROP_WORDS):
            return "CROP_RECOMMENDATION"
        if any(w in lowered for w in self._WHY_WORDS):
            return "REPORT_REASON"
        if any(w in lowered for w in self._WATER_WORDS):
            return "GENERAL_INFORMATION"
        return "GENERAL_INFORMATION"

    def _select_tools(self, state: AgentState) -> dict[str, Any]:
        intent = state["intent"]
        tool_map: dict[str, list[str]] = {
            "REPORT_REASON": ["get_region_analysis", "get_report_sources"],
            "RISK_EXPLANATION": ["get_region_analysis", "get_report_sources"],
            "CROP_RECOMMENDATION": ["get_region_analysis", "get_report_sources"],
            "TERM_EXPLANATION": ["explain_agricultural_term"],
            "OFFICIAL_GUIDANCE": ["search_official_guidance", "get_report_sources"],
            "GENERAL_INFORMATION": ["get_region_analysis", "get_report_sources"],
            "UNSUPPORTED_ACTION": [],
        }
        selected = tool_map.get(intent, ["get_region_analysis"])
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
        return {"tool_results": results, "trace": [*state["trace"], f"도구 실행 완료: {list(results.keys())}"]}

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
        if not region_analysis:
            answer = StructuredAnswer(
                answer="완료된 분석 리포트를 아직 확인하지 못했습니다. 지역 환경 분석이 완료된 뒤 다시 질문해 주시면 리포트의 점수·위험 요소·추천 작물 근거를 바탕으로 안내할게요.",
                basisType="CURRENT_REPORT",
            )
            return {"structured_answer": answer, "trace": [*state["trace"], "리포트 없음 응답 생성"]}

        if settings.OPENAI_API_KEY and settings.LLM_PROVIDER.lower() == "openai":
            llm_answer = await self._call_openai(fp, intent, tool_results)
            if llm_answer is not None:
                return {"structured_answer": llm_answer, "trace": [*state["trace"], "LLM 응답 생성"]}

        fallback_answer = self._build_deterministic_answer(fp, intent, region_analysis, tool_results)
        return {"structured_answer": fallback_answer, "trace": [*state["trace"], "규칙 기반 응답 생성"]}

    async def _call_openai(
        self, fp: FactPackage, intent: str, tool_results: dict[str, Any]
    ) -> StructuredAnswer | None:
        facts_text = "\n".join(f"{k}: {v}" for k, v in fp.facts.items() if v is not None)
        sources_text = "\n".join(
            f"- {s.get('provider', '')} {s.get('service', '')} ({s.get('dataDate', '')})"
            for s in fp.sources
        )
        system_prompt = (
            "당신은 Farmflate 농사 안내 도우미입니다. 반드시 제공된 Fact만 사용해 한국어로 답하세요. "
            "제공되지 않은 수치, 작물, 위험, 공공 API 결과를 추정하거나 만들어 내지 마세요. "
            "농약량·비료량 처방, 병해충 확정 판단을 하지 마세요. "
            "반드시 아래 JSON 형식으로만 응답하세요:\n"
            '{"answer": "답변 텍스트", "basisType": "CURRENT_REPORT", '
            '"usedFactIds": ["사용한 fact key"], "usedSourceIds": ["사용한 sourceId"], '
            '"mentionedNumbers": [숫자], "mentionedCrops": ["작물명"], "mentionedRisks": ["위험명"], '
            '"safetyNotice": null}\n'
            "safetyNotice는 토양수분 센서 부재 등 불확실성이 있을 때만 작성하세요."
        )
        user_content = f"질문: {fp.question}\n\nFact:\n{facts_text}\n\n출처:\n{sources_text or '없음'}"
        messages = [
            {"role": "system", "content": system_prompt},
            *[{"role": h.get("role", "user"), "content": h.get("content", "")} for h in fp.history[-8:]],
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

    def _build_deterministic_answer(
        self, fp: FactPackage, intent: str, region_analysis: dict[str, Any], tool_results: dict[str, Any]
    ) -> StructuredAnswer:
        facts = fp.facts
        used_fact_ids: list[str] = []
        used_source_ids = [s.get("sourceId", "") for s in fp.sources if isinstance(s, dict)]
        mentioned_numbers: list[float] = []
        mentioned_crops: list[str] = []
        mentioned_risks: list[str] = []

        region_name = facts.get("region.name", "")
        region_score = facts.get("region.score")
        region_grade = facts.get("region.grade", "")

        if intent == "RISK_EXPLANATION":
            risk_title = facts.get("risk.1.title", "")
            risk_action = facts.get("risk.1.action.1", "")
            parts = []
            if region_name:
                parts.append(f"{region_name} 분석 기준")
                used_fact_ids.append("region.name")
            if risk_title:
                parts.append(f"가장 큰 위험은 '{risk_title}'입니다.")
                used_fact_ids.append("risk.1.title")
                mentioned_risks.append(str(risk_title))
            if risk_action:
                parts.append(f"권장 행동: {risk_action}")
                used_fact_ids.append("risk.1.action.1")
            if not parts:
                parts.append("현재 리포트에 표시된 핵심 위험 항목이 없습니다.")
            answer_text = " ".join(parts)

        elif intent == "CROP_RECOMMENDATION":
            crop_parts = []
            for i in range(1, 4):
                name = facts.get(f"crop.{i}.name")
                score = facts.get(f"crop.{i}.score")
                if name:
                    label = str(name)
                    if score is not None:
                        label += f" {score}점"
                        mentioned_numbers.append(float(score))
                    crop_parts.append(label)
                    used_fact_ids.append(f"crop.{i}.name")
                    if score is not None:
                        used_fact_ids.append(f"crop.{i}.score")
                    mentioned_crops.append(str(name))
            if crop_parts:
                answer_text = f"추천 작물은 {', '.join(crop_parts)}입니다."
                if region_score is not None:
                    answer_text += f" 지역 종합 점수는 {region_score}점입니다."
                    used_fact_ids.append("region.score")
                    mentioned_numbers.append(float(region_score))
            else:
                answer_text = "현재 리포트에 추천 작물 데이터가 없습니다."

        elif intent == "REPORT_REASON":
            parts = []
            if region_name:
                parts.append(f"{region_name}의")
                used_fact_ids.append("region.name")
            if region_score is not None:
                parts.append(f"지역 점수는 {region_score}점입니다.")
                used_fact_ids.append("region.score")
                mentioned_numbers.append(float(region_score))
            if region_grade:
                parts.append(f"등급은 {region_grade}입니다.")
                used_fact_ids.append("region.grade")
            crop1 = facts.get("crop.1.name")
            if crop1:
                parts.append(f"최우선 추천 작물은 {crop1}입니다.")
                used_fact_ids.append("crop.1.name")
                mentioned_crops.append(str(crop1))
            answer_text = " ".join(parts) if parts else "리포트의 세부 근거를 확인해 주세요."

        elif intent == "OFFICIAL_GUIDANCE":
            guidance = tool_results.get("official_guidance", [])
            tips = [g for g in guidance if g.get("type") == "tip"]
            if tips:
                answer_text = tips[0]["content"]
                used_fact_ids.append(tips[0].get("factKey", ""))
            else:
                answer_text = "현재 리포트에 저장된 공식 자료가 없습니다. 농사로(nongsaro.go.kr)에서 직접 확인하실 수 있습니다."

        else:
            parts = []
            if region_score is not None:
                parts.append(f"지역 점수는 {region_score}점입니다.")
                used_fact_ids.append("region.score")
                mentioned_numbers.append(float(region_score))
            risk_title = facts.get("risk.1.title")
            if risk_title:
                parts.append(f"주요 위험은 '{risk_title}'입니다.")
                used_fact_ids.append("risk.1.title")
                mentioned_risks.append(str(risk_title))
            crop1 = facts.get("crop.1.name")
            if crop1:
                parts.append(f"추천 1순위 작물은 {crop1}입니다.")
                used_fact_ids.append("crop.1.name")
                mentioned_crops.append(str(crop1))
            answer_text = " ".join(parts) if parts else "완료된 분석 리포트를 확인한 뒤 다시 질문해 주세요."

        if self._is_watering_question(fp.question):
            answer_text = (
                "실제 토양수분 센서값이 없어 물주기 필요 여부를 확정할 수 없습니다. "
                "최근 강수와 예보를 확인하고 뿌리 주변 흙의 수분 상태를 직접 확인하세요."
            )
            used_fact_ids = [k for k in used_fact_ids if k.startswith("region.")]

        return StructuredAnswer(
            answer=answer_text,
            basisType="CURRENT_REPORT",
            usedFactIds=[f for f in used_fact_ids if f],
            usedSourceIds=used_source_ids,
            mentionedNumbers=mentioned_numbers,
            mentionedCrops=mentioned_crops,
            mentionedRisks=mentioned_risks,
            safetyNotice="실제 토양수분 센서값이 없어 물주기 필요 여부를 확정할 수 없습니다." if self._is_watering_question(fp.question) else None,
        )

    @staticmethod
    def _is_watering_question(question: str) -> bool:
        lowered = question.replace(" ", "")
        return any(w in lowered for w in ("물", "급수", "관수", "물주기"))

    def _validate_facts_and_sources(self, state: AgentState) -> dict[str, Any]:
        answer = state.get("structured_answer")
        if answer is None:
            return {"validation_passed": False, "trace": [*state["trace"], "답변 없음 — 검증 실패"]}

        fp = state["fact_package"]
        valid_fact_keys = set(fp.facts.keys())
        valid_source_ids = {s.get("sourceId", "") for s in fp.sources if isinstance(s, dict)}
        fact_values = set()
        for v in fp.facts.values():
            if isinstance(v, (int, float)):
                fact_values.add(float(v))
        crop_names = set()
        risk_titles = set()
        for k, v in fp.facts.items():
            if k.startswith("crop.") and k.endswith(".name") and isinstance(v, str):
                crop_names.add(v)
            if k.startswith("risk.") and k.endswith(".title") and isinstance(v, str):
                risk_titles.add(v)

        errors: list[str] = []
        for fid in answer.usedFactIds:
            if fid and fid not in valid_fact_keys:
                errors.append(f"Fact '{fid}' not in FactPackage")
        for sid in answer.usedSourceIds:
            if sid and sid not in valid_source_ids:
                errors.append(f"Source '{sid}' not in sources")
        for num in answer.mentionedNumbers:
            if float(num) not in fact_values:
                errors.append(f"Number {num} not in fact values")
        for crop in answer.mentionedCrops:
            if crop and crop not in crop_names:
                errors.append(f"Crop '{crop}' not in fact crops")
        for risk in answer.mentionedRisks:
            if risk and risk not in risk_titles:
                errors.append(f"Risk '{risk}' not in fact risks")

        if answer.basisType in ("CURRENT_REPORT", "OFFICIAL_GUIDANCE") and not answer.usedSourceIds:
            if fp.sources:
                errors.append("basisType requires sources but usedSourceIds is empty")

        number_pattern = re.compile(r"(\d+(?:\.\d+)?)\s*(?:점|℃|%|mm|m/s)")
        for match in number_pattern.finditer(answer.answer):
            try:
                num = float(match.group(1))
                if num not in fact_values:
                    errors.append(f"Answer number {num} not in facts")
            except ValueError:
                pass

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

        facts = fp.facts
        parts: list[str] = []
        used_ids: list[str] = []
        mentioned_nums: list[float] = []
        mentioned_crops_list: list[str] = []
        mentioned_risks_list: list[str] = []

        score = facts.get("region.score")
        grade = facts.get("region.grade")
        if score is not None:
            grade_str = f"({grade})" if grade else ""
            parts.append(f"현재 리포트의 지역 점수는 {score}점{grade_str}입니다.")
            used_ids.append("region.score")
            mentioned_nums.append(float(score))

        crop_parts = []
        for i in range(1, 4):
            name = facts.get(f"crop.{i}.name")
            cscore = facts.get(f"crop.{i}.score")
            if name:
                label = str(name)
                if cscore is not None:
                    label += f"({cscore}점)"
                    mentioned_nums.append(float(cscore))
                    used_ids.append(f"crop.{i}.score")
                crop_parts.append(label)
                used_ids.append(f"crop.{i}.name")
                mentioned_crops_list.append(str(name))
        if crop_parts:
            parts.append(f"추천 작물은 {', '.join(crop_parts)}입니다.")

        risk_title = facts.get("risk.1.title")
        if risk_title:
            parts.append(f"가장 큰 위험은 '{risk_title}'입니다.")
            used_ids.append("risk.1.title")
            mentioned_risks_list.append(str(risk_title))
            risk_action = facts.get("risk.1.action.1")
            if risk_action:
                parts.append(f"권장 행동은 {risk_action}입니다.")
                used_ids.append("risk.1.action.1")
        elif score is not None:
            parts.append("현재 기상 예보에서 확인된 주요 위험은 없습니다.")

        if not parts:
            parts.append("완료된 분석 리포트를 확인한 뒤 다시 질문해 주세요.")

        fallback = StructuredAnswer(
            answer=" ".join(parts),
            basisType="CURRENT_REPORT",
            usedFactIds=used_ids,
            usedSourceIds=[s.get("sourceId", "") for s in fp.sources if isinstance(s, dict)],
            mentionedNumbers=mentioned_nums,
            mentionedCrops=mentioned_crops_list,
            mentionedRisks=mentioned_risks_list,
        )
        return {
            "final_answer": fallback,
            "final_sources": fp.sources,
            "trace": [*state["trace"], "Deterministic fallback 반환"],
        }

    @staticmethod
    def _chunks(value: str, width: int) -> Iterable[str]:
        return (value[i : i + width] for i in range(0, len(value), width))


ai_service = AIService()
