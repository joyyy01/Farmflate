from __future__ import annotations

import json
import re
from typing import Any

import httpx

from app.core.config import settings
from app.core.outbound_http import outbound_http_client
from app.schemas.chat import FieldGuidanceRequest, FieldGuidanceResponse, FieldGuidanceTask
from app.services.knowledge_catalog import CROP_PROFILES


class FieldGuidanceService:
    """Produces the strict field-guidance JSON contract from verified rule facts."""

    async def generate(self, request: FieldGuidanceRequest) -> FieldGuidanceResponse:
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
        headline_description = (tasks[0].description if tasks else "현재 확인된 환경 정보를 바탕으로 작물 상태를 점검해 주세요.")[:300]
        reasoning_summary = self._build_reasoning(facts, crop_name, tasks, alert_titles)
        if settings.OPENAI_API_KEY and settings.LLM_PROVIDER.lower() == "openai":
            llm_summary = await self._call_reasoning(facts, crop_name, reasoning_summary)
            if llm_summary is not None:
                reasoning_summary = llm_summary
        return FieldGuidanceResponse(
            headline=headline,
            headlineDescription=headline_description,
            tasks=tasks,
            reasoningSummary=reasoning_summary,
        )

    @staticmethod
    def _build_reasoning(
        facts: dict[str, Any], crop_name: str, tasks: list[FieldGuidanceTask], alert_titles: list[str]
    ) -> str:
        reasoning_points = facts.get("reasoningPoints") if isinstance(facts.get("reasoningPoints"), list) else []
        reason = next((str(point).strip() for point in reasoning_points if str(point).strip()), "")
        alert = alert_titles[0] if alert_titles else "오늘의 환경 변화"
        action = tasks[0].title if tasks else "밭 상태 확인"
        if reason:
            return f"{crop_name}에 {alert}가 예상돼요. {reason} 그래서 {action}을 먼저 안내했어요."[:500]
        return f"{crop_name}의 오늘 환경을 확인한 결과 {alert}에 대비해 {action}을 먼저 안내했어요."[:500]

    async def _call_reasoning(self, facts: dict[str, Any], crop_name: str, fallback: str) -> str | None:
        system_prompt = (
            "당신은 초보 농업인을 위한 Farmflate 안내문 작성기입니다. 제공된 사실만 사용해 '왜 이렇게 안내했나요?' "
            "요약을 한국어 한두 문장으로 작성하세요. 작물, 경고, 작업, 수치는 제공된 사실 밖으로 만들지 마세요. "
            "원시 숫자만 나열하지 말고 조건-영향-확인 행동을 연결하세요. 반드시 JSON만 반환하세요: "
            '{"reasoningSummary":"요약"}'
        )
        try:
            response = await outbound_http_client.post(
                f"{settings.OPENAI_BASE_URL.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                json={
                    "model": settings.OPENAI_MODEL,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": f"검증된 사실: {self._safe_prompt_json(facts)}\n기본 안전 요약: {fallback}"},
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
            return summary if self._is_valid_summary(summary, facts, crop_name) else None
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            return None

    @staticmethod
    def _safe_prompt_json(value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, default=str)[:12_000]

    @staticmethod
    def _is_valid_summary(summary: str, facts: dict[str, Any], crop_name: str) -> bool:
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
        known_crops = {profile["name"] for profile in CROP_PROFILES.values()}
        mentions_condition = not alert_titles or any(title in summary for title in alert_titles)
        mentions_action = not task_titles or any(title in summary for title in task_titles)
        return (
            not any(crop in summary for crop in known_crops - {crop_name})
            and bool(task_titles or alert_titles)
            and mentions_condition
            and mentions_action
        )
