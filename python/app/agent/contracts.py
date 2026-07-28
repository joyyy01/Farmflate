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
class AgentResult:
    answer: str
    status: Literal["completed", "needs_context", "failed"]
    citation_ids: list[str] = field(default_factory=list)
    citations: list[ToolCitation] = field(default_factory=list)
    safety_notice: str | None = None
    trace: list[str] = field(default_factory=list)
