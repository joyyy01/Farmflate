from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class RetrievedChunk:
    chunk_id: str
    document_id: str
    source_id: str
    source_name: str
    canonical_url: str
    title: str | None
    content: str
    score: float
    metadata: dict[str, Any]

    def citation(self) -> dict[str, str]:
        return {
            "sourceId": self.source_id,
            "title": self.title or self.source_name,
            "sourceUrl": self.canonical_url,
            "chunkId": self.chunk_id,
        }


@dataclass(frozen=True)
class RetrievalResult:
    chunks: list[RetrievedChunk]
    query: str
    insufficient_evidence: bool

    def tool_payload(self) -> dict[str, Any]:
        return {
            "query": self.query,
            "insufficientEvidence": self.insufficient_evidence,
            "citations": [chunk.citation() for chunk in self.chunks],
            "evidence": [
                {"chunkId": chunk.chunk_id, "content": chunk.content, "score": round(chunk.score, 6)}
                for chunk in self.chunks
            ],
        }
