from __future__ import annotations

from typing import Any

from app.agent.contracts import ToolCitation, ToolResult
from app.rag.retriever import HybridRetriever, rag_retriever
from app.schemas.chat import FactPackage


TOOL_DEFINITIONS = [
    {
        "type": "function",
        "name": "read_authorized_context",
        "description": "Read already-authorized Farmflate facts for one screen section. This tool never accepts a resource ID.",
        "parameters": {
            "type": "object",
            "properties": {"section": {"type": "string", "enum": ["summary", "climate", "soil", "hazard", "crop", "field", "all"]}},
            "required": ["section"],
            "additionalProperties": False,
        },
        "strict": True,
    },
    {
        "type": "function",
        "name": "search_approved_knowledge",
        "description": "Search approved, current agricultural knowledge. Use it for factual guidance not contained in authorized Farmflate facts.",
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string", "minLength": 1, "maxLength": 500}},
            "required": ["query"],
            "additionalProperties": False,
        },
        "strict": True,
    },
]


class AgentToolExecutor:
    _SECTION_PREFIXES = {
        "summary": ("region.",),
        "climate": ("climate.", "weather."),
        "soil": ("soil.", "field.soil."),
        "hazard": ("risk.", "field.alert."),
        "crop": ("crop.",),
        "field": ("field.",),
    }

    def __init__(self, retriever: HybridRetriever = rag_retriever) -> None:
        self._retriever = retriever

    async def execute(self, *, name: str, arguments: dict[str, Any], fact_package: FactPackage) -> ToolResult:
        if name == "read_authorized_context":
            return self._read_authorized_context(arguments, fact_package)
        if name == "search_approved_knowledge":
            return await self._search_approved_knowledge(arguments)
        return ToolResult(status="tool_not_allowed")

    def _read_authorized_context(self, arguments: dict[str, Any], fact_package: FactPackage) -> ToolResult:
        section = arguments.get("section")
        if section not in {*self._SECTION_PREFIXES, "all"}:
            return ToolResult(status="invalid_arguments")
        prefixes = () if section == "all" else self._SECTION_PREFIXES[section]
        facts = {
            key: value for key, value in fact_package.facts.items()
            if not prefixes or key.startswith(prefixes)
        }
        citations = [
            ToolCitation(
                citation_id=f"fact:{source.get('sourceId')}",
                title=str(source.get("provider") or source.get("service") or source.get("sourceId")),
                source_url=source.get("sourceUrl"),
            )
            for source in fact_package.sources
            if isinstance(source, dict) and source.get("sourceId")
        ]
        return ToolResult(status="ok", payload={"section": section, "facts": facts}, citations=citations)

    async def _search_approved_knowledge(self, arguments: dict[str, Any]) -> ToolResult:
        query = arguments.get("query")
        if not isinstance(query, str) or not query.strip() or len(query) > 500:
            return ToolResult(status="invalid_arguments")
        result = await self._retriever.retrieve(query)
        citations = [
            ToolCitation(
                citation_id=str(item["citationId"]), title=str(item["title"]), source_url=item.get("sourceUrl")
            )
            for chunk in result.chunks
            for item in [chunk.citation()]
        ]
        return ToolResult(
            status="insufficient_evidence" if result.insufficient_evidence else "ok",
            payload={
                "query": result.query,
                "evidence": [
                    {"citationId": chunk.citation()["citationId"], "content": chunk.content[:1200], "score": round(chunk.score, 6)}
                    for chunk in result.chunks
                ],
            },
            citations=citations,
        )
