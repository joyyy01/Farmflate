from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Any, Sequence

from app.agent.contracts import ToolCitation
from app.rag.models import RetrievedChunk


_SUSPICIOUS_INSTRUCTION = re.compile(
    r"ignore\s+(all\s+)?previous|reveal\s+(the\s+)?system\s+prompt|"
    r"developer\s+message|call\s+(a\s+)?tool|<\s*system\s*>|"
    r"이전\s*지시|앞선\s*지시|시스템\s*프롬프트|도구를?\s*호출|비밀.*(?:공개|보여)",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class PreparedEvidence:
    payload: list[dict[str, Any]]
    citations: list[ToolCitation]
    excluded_count: int


def prepare_retrieved_evidence(chunks: Sequence[RetrievedChunk]) -> PreparedEvidence:
    """Keep retrieved text as untrusted data and drop obvious instruction-shaped payloads."""
    payload: list[dict[str, Any]] = []
    citations: list[ToolCitation] = []
    excluded_count = 0
    for chunk in chunks:
        content = normalize_external_text(chunk.content)
        if _SUSPICIOUS_INSTRUCTION.search(content):
            excluded_count += 1
            continue
        citation = chunk.citation()
        payload.append({
            "citationId": citation["citationId"],
            "title": citation["title"],
            "sourceUrl": citation["sourceUrl"],
            "untrusted": True,
            "content": content[:1200],
            "retrievalPaths": list(chunk.retrieval_paths),
        })
        citations.append(ToolCitation(
            citation_id=str(citation["citationId"]),
            title=str(citation["title"]),
            source_url=citation["sourceUrl"],
        ))
    return PreparedEvidence(payload=payload, citations=citations, excluded_count=excluded_count)


def normalize_external_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value)
    return "".join(
        character if character in "\n\t" or unicodedata.category(character)[0] != "C" else " "
        for character in normalized
    ).strip()
