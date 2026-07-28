from __future__ import annotations

from app.core.config import settings
from app.rag.models import RetrievalResult
from app.rag.repository import RagRepository


class PostgresRagRetriever:
    """Approved-knowledge retrieval backed only by PostgreSQL full-text search."""

    def __init__(self, repository: RagRepository | None = None) -> None:
        self._repository = repository or (RagRepository(settings.RAG_DATABASE_URL) if settings.RAG_DATABASE_URL else None)

    @property
    def available(self) -> bool:
        return self._repository is not None

    async def retrieve(self, query: str, *, language: str | None = "ko") -> RetrievalResult:
        normalized = query.strip()
        if not self.available or not normalized:
            return RetrievalResult(chunks=[], query=normalized, insufficient_evidence=True)
        chunks = await self._repository.search(
            query_text=normalized,
            top_k=settings.RAG_TOP_K,
            language=language,
        )
        return RetrievalResult(chunks=chunks, query=normalized, insufficient_evidence=not chunks)

    async def close(self) -> None:
        if self._repository is not None:
            await self._repository.close()


# Retain the prior import name for callers while the implementation is now
# deliberately PostgreSQL FTS-only.
HybridRetriever = PostgresRagRetriever
rag_retriever = PostgresRagRetriever()
