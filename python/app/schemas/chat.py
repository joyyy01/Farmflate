from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


class ChatMessage(BaseModel):
    """A bounded prior turn supplied by the client."""

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=1_200)

    @field_validator("content")
    @classmethod
    def trim_content(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("메시지 내용은 비워 둘 수 없습니다.")
        return value


class ChatPageContext(BaseModel):
    """Read-only facts from the page currently visible to the user.

    The server deliberately accepts only page data.  It never receives API keys,
    authentication tokens, or browser storage values.
    """

    region: str | None = Field(default=None, max_length=120)
    selected_crop: str | None = Field(default=None, max_length=80)
    report: dict[str, Any] = Field(default_factory=dict)
    home: dict[str, Any] = Field(default_factory=dict)
    fields: list[dict[str, Any]] = Field(default_factory=list, max_length=20)

    @field_validator("region", "selected_crop")
    @classmethod
    def trim_optional_text(cls, value: str | None) -> str | None:
        return value.strip() if value and value.strip() else None


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1_200)
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)
    context: ChatPageContext | None = None
    temperature: float = Field(default=0.2, ge=0.0, le=1.0)

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


class ChatResponse(BaseModel):
    reply: str
    status: Literal["grounded", "needs_context"] = "needs_context"
    sources: list[GroundingSource] = Field(default_factory=list)
    used_context: list[str] = Field(default_factory=list)
    agent_steps: list[str] = Field(default_factory=list)


class AgentTaskRequest(BaseModel):
    task: str = Field(min_length=1, max_length=1_200)
    context: ChatPageContext | None = None
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
