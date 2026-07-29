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
    retrieval_paths: tuple[str, ...] = ()

    def citation(self) -> dict[str, str]:
        return {
            "citationId": f"rag:{self.chunk_id}",
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
    mode: str = "lexical"
    latency_ms: int = 0
    candidate_count: int = 0

    def tool_payload(self) -> dict[str, Any]:
        return {
            "query": self.query,
            "insufficientEvidence": self.insufficient_evidence,
            "retrievalMode": self.mode,
            "latencyMs": self.latency_ms,
            "candidateCount": self.candidate_count,
            "citations": [chunk.citation() for chunk in self.chunks],
            "evidence": [
                {
                    "chunkId": chunk.chunk_id,
                    "content": chunk.content,
                    "score": round(chunk.score, 6),
                    "retrievalPaths": list(chunk.retrieval_paths),
                }
                for chunk in self.chunks
            ],
        }
