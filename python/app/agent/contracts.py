from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal


@dataclass(frozen=True)
class ToolCitation:
    citation_id: str
    title: str
    source_url: str | None = None


@dataclass(frozen=True)
class ToolResult:
    status: str
    payload: dict[str, Any] = field(default_factory=dict)
    citations: list[ToolCitation] = field(default_factory=list)
    trace: list[str] = field(default_factory=list)

    def model_output(self) -> str:
        import json

        return json.dumps({
            "status": self.status,
            "payload": self.payload,
            "citations": [citation.__dict__ for citation in self.citations],
        }, ensure_ascii=False)


@dataclass(frozen=True)
class ToolCall:
    call_id: str
    name: str
    arguments: dict[str, Any]


@dataclass(frozen=True)
class AgentDraft:
    answer: str
    claims: list[dict[str, Any]]
    citation_ids: list[str]
    status: Literal["completed", "needs_context"]
    safety_notice: str | None = None


@dataclass(frozen=True)
class AgentExecutionTelemetry:
    """Privacy-safe aggregate measurements for one bounded Agent run."""

    terminal_status: Literal["completed", "needs_context", "failed"]
    terminal_reason: str
    model_turn_count: int
    tool_call_count: int
    tool_non_success_count: int
    citation_count: int
    answer_char_count: int
    total_latency_ms: int
    model_latency_ms: int
    tool_latency_ms: int
    tool_statuses: tuple[str, ...] = ()


@dataclass(frozen=True)
class AgentResult:
    answer: str
    status: Literal["completed", "needs_context", "failed"]
    citation_ids: list[str] = field(default_factory=list)
    citations: list[ToolCitation] = field(default_factory=list)
    safety_notice: str | None = None
    trace: list[str] = field(default_factory=list)
    telemetry: AgentExecutionTelemetry | None = None
