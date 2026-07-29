from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


_MODEL_INPUT_MAX_DEPTH = 4
_MODEL_INPUT_MAX_ITEMS = 64
_MODEL_INPUT_MAX_STRING_CHARS = 4_000
_MODEL_INPUT_MAX_TOTAL_CHARS = 16_000


def _validate_model_input_budget(value: object, *, field_name: str) -> object:
    """Reject oversized JSON-like internal payloads before they reach an LLM."""
    item_count = 0
    total_chars = 0

    def visit(current: object, depth: int) -> None:
        nonlocal item_count, total_chars
        if depth > _MODEL_INPUT_MAX_DEPTH:
            raise ValueError(f"{field_name} exceeds the maximum nesting depth.")
        if isinstance(current, dict):
            for key, child in current.items():
                item_count += 1
                if item_count > _MODEL_INPUT_MAX_ITEMS:
                    raise ValueError(f"{field_name} exceeds the maximum item count.")
                visit(str(key), depth + 1)
                visit(child, depth + 1)
            return
        if isinstance(current, list):
            for child in current:
                item_count += 1
                if item_count > _MODEL_INPUT_MAX_ITEMS:
                    raise ValueError(f"{field_name} exceeds the maximum item count.")
                visit(child, depth + 1)
            return
        if isinstance(current, str):
            if len(current) > _MODEL_INPUT_MAX_STRING_CHARS:
                raise ValueError(f"{field_name} contains an oversized string.")
            total_chars += len(current)
            if total_chars > _MODEL_INPUT_MAX_TOTAL_CHARS:
                raise ValueError(f"{field_name} exceeds the total character budget.")
            return
        if current is None or isinstance(current, (bool, int, float)):
            return
        raise ValueError(f"{field_name} must contain JSON-compatible values.")

    visit(value, depth=0)
    return value


# ---------------------------------------------------------------------------
# Chat models kept for the existing /chat endpoint contract.
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

    @field_validator("context")
    @classmethod
    def validate_context_budget(cls, value: dict[str, Any] | None) -> dict[str, Any] | None:
        if value is None:
            return value
        return _validate_model_input_budget(value, field_name="context")


class GroundingSource(BaseModel):
    title: str
    detail: str | None = None
    observed_at: str | None = None
    source_url: str | None = None
    evidence_count: int = Field(default=1, ge=1, serialization_alias="evidenceCount")


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

    @field_validator("userScope", "context", "facts", "sources")
    @classmethod
    def validate_model_input_budget(cls, value: object, info: object) -> object:
        field_name = getattr(info, "field_name", "model input")
        return _validate_model_input_budget(value, field_name=field_name)

    @field_validator("sources")
    @classmethod
    def validate_source_provenance(cls, value: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """Keep existing sources readable while validating optional fact provenance.

        A source without ``factKeyPrefixes`` is deliberately not rejected: old
        Farmflate routes can still send it, but the Agent tool will never use
        it as support for a completed answer.
        """
        normalized: list[dict[str, Any]] = []
        for source in value:
            if not isinstance(source, dict):
                continue
            copy = dict(source)
            prefixes = copy.get("factKeyPrefixes")
            if prefixes is not None:
                if not isinstance(prefixes, list) or any(not isinstance(prefix, str) for prefix in prefixes):
                    raise ValueError("source.factKeyPrefixes must be a list of strings.")
                copy["factKeyPrefixes"] = [prefix.strip() for prefix in prefixes if prefix.strip()]
            normalized.append(copy)
        return normalized


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
    trace: list[str] = Field(default_factory=list)


# ---------------------------------------------------------------------------
# Task-agent models for the existing endpoint contract.
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
