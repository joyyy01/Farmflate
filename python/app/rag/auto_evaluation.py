from __future__ import annotations

import json
from dataclasses import dataclass
from hashlib import sha256

import httpx


@dataclass(frozen=True)
class AutoEvaluationCase:
    case_key: str
    query_text: str
    expected_chunk_id: str


class PublicContentQuestionGenerator:
    """Generate silver retrieval questions from explicitly permitted public text."""

    def __init__(self, *, api_key: str, model: str, timeout_seconds: float = 30.0) -> None:
        if not api_key.strip():
            raise ValueError("OPENAI_API_KEY must be configured for automatic evaluation generation.")
        self._api_key = api_key.strip()
        self._model = model
        self._timeout_seconds = timeout_seconds

    async def generate(self, *, chunk_id: str, content: str, count: int) -> list[AutoEvaluationCase]:
        prompt = (
            "다음 공개 농업 문서 조각만 근거로, 사용자가 실제로 물을 법한 한국어 검색 질문을 정확히 "
            f"{count}개 만드세요. 답이나 설명은 쓰지 말고 JSON 객체 {{\"questions\":[\"...\"]}}만 반환하세요. "
            "문서에 없는 수치·농약명·시기·작물은 만들지 말고, 서로 의미가 겹치지 않게 하세요.\n\n"
            f"문서 조각:\n{content[:6000]}"
        )
        payload = {
            "model": self._model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.2,
            "response_format": {"type": "json_object"},
        }
        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.post(
                "https://api.openai.com/v1/chat/completions",
                headers={"Authorization": f"Bearer {self._api_key}"},
                json=payload,
            )
            response.raise_for_status()
        raw = response.json()["choices"][0]["message"]["content"]
        parsed = json.loads(raw)
        questions = parsed.get("questions") if isinstance(parsed, dict) else None
        if not isinstance(questions, list):
            raise ValueError("Automatic evaluator did not return a questions list.")
        result: list[AutoEvaluationCase] = []
        for ordinal, question in enumerate(questions[:count], start=1):
            if not isinstance(question, str) or not 8 <= len(question.strip()) <= 300:
                continue
            digest = sha256(f"{chunk_id}:{question.strip()}".encode()).hexdigest()[:16]
            result.append(AutoEvaluationCase(
                case_key=f"auto-{digest}-{ordinal}",
                query_text=question.strip(),
                expected_chunk_id=chunk_id,
            ))
        if len(result) != count:
            raise ValueError("Automatic evaluator returned fewer valid questions than requested.")
        return result
