from __future__ import annotations

from dataclasses import dataclass

from app.core.config import settings
from app.rag.manifest import sha256_text


@dataclass(frozen=True)
class PreparedChunk:
    ordinal: int
    content: str
    content_sha256: str


def chunk_document(content: str, max_chars: int | None = None) -> list[PreparedChunk]:
    """Deterministic, bounded chunks for an operator ingestion job."""
    limit = max_chars or settings.RAG_MAX_CHUNK_CHARS
    paragraphs = [part.strip() for part in content.split("\n\n") if part.strip()]
    chunks: list[PreparedChunk] = []
    current = ""
    for paragraph in paragraphs:
        while paragraph:
            room = limit - len(current) - (2 if current else 0)
            if room <= 0:
                chunks.append(PreparedChunk(len(chunks), current, sha256_text(current)))
                current = ""
                room = limit
            piece, paragraph = paragraph[:room], paragraph[room:]
            current = f"{current}\n\n{piece}".strip()
            if len(current) >= limit:
                chunks.append(PreparedChunk(len(chunks), current, sha256_text(current)))
                current = ""
    if current:
        chunks.append(PreparedChunk(len(chunks), current, sha256_text(current)))
    return chunks
