from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


# ---------------------------------------------------------------------------
# Legacy chat models (kept for backward-compatible /chat/ endpoint)
# ---------------------------------------------------------------------------

class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=1_200)

    @field_validator("content")
    @classmethod
    def trim_content(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("메시지 내용은 비워 둘 수 없습니다.")
        return value


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1_200)
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)
    context: dict[str, Any] | None = None

    @field_validator("message")
    @classmethod
    def trim_message(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("질문을 입력해 주세요.")
        return value


class GroundingSource(BaseModel):
    title: str
    detail: str | None = None
    observed_at: str | None = None
    source_url: str | None = None


class ChatResponse(BaseModel):
    reply: str
    status: Literal["grounded", "needs_context"] = "needs_context"
    sources: list[GroundingSource] = Field(default_factory=list)
    used_context: list[str] = Field(default_factory=list)
    agent_steps: list[str] = Field(default_factory=list)


# ---------------------------------------------------------------------------
# FactPackage models (Spring Boot AI proxy → Python Agent)
# ---------------------------------------------------------------------------

class FactPackage(BaseModel):
    requestId: str
    userScope: dict[str, Any] = Field(default_factory=dict)
    question: str = Field(..., min_length=1, max_length=1_200)
    history: list[dict[str, Any]] = Field(default_factory=list, max_length=12)
    context: dict[str, Any] = Field(default_factory=dict)
    facts: dict[str, Any] = Field(default_factory=dict)
    sources: list[dict[str, Any]] = Field(default_factory=list)

    @field_validator("question")
    @classmethod
    def trim_question(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("질문을 입력해 주세요.")
        return value

    @field_validator("history")
    @classmethod
    def sanitize_history(cls, value: list[dict[str, Any]]) -> list[dict[str, str]]:
        sanitized: list[dict[str, str]] = []
        for message in value[-8:]:
            if not isinstance(message, dict):
                continue
            role = message.get("role")
            content = message.get("content")
            if role not in ("user", "assistant") or not isinstance(content, str):
                continue
            content = content.strip()
            if content:
                sanitized.append({"role": role, "content": content[:1200]})
        return sanitized


class AgentRunRequest(BaseModel):
    fact_package: FactPackage


class StructuredAnswer(BaseModel):
    answer: str
    basisType: str = "CURRENT_REPORT"
    usedFactIds: list[str] = Field(default_factory=list)
    usedSourceIds: list[str] = Field(default_factory=list)
    mentionedNumbers: list[float] = Field(default_factory=list)
    mentionedCrops: list[str] = Field(default_factory=list)
    mentionedRisks: list[str] = Field(default_factory=list)
    safetyNotice: str | None = None


class AgentRunResponse(BaseModel):
    requestId: str
    status: str = "completed"
    answer: StructuredAnswer
    sources: list[dict[str, Any]] = Field(default_factory=list)


# ---------------------------------------------------------------------------
# Legacy agent models (kept for backward compat)
# ---------------------------------------------------------------------------

class AgentTaskRequest(BaseModel):
    task: str = Field(min_length=1, max_length=1_200)
    context: dict[str, Any] | None = None
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)

    @field_validator("task")
    @classmethod
    def trim_task(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("에이전트 작업을 입력해 주세요.")
        return value


class AgentTaskResponse(BaseModel):
    task_id: str
    status: Literal["completed", "needs_context"]
    result: str
    steps_taken: list[str]
    sources: list[GroundingSource] = Field(default_factory=list)


class FieldGuidanceTask(BaseModel):
    key: str
    title: str
    description: str


class FieldGuidanceRequest(BaseModel):
    facts: dict[str, Any] = Field(default_factory=dict)


class FieldGuidanceResponse(BaseModel):
    headline: str
    headlineDescription: str
    tasks: list[FieldGuidanceTask] = Field(default_factory=list)
    reasoningSummary: str
