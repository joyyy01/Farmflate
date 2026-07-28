from __future__ import annotations

from app.core.config import settings
from app.rag.models import RetrievalResult
from app.rag.repository import RagRepository


class PostgresRagRetriever:
    """PostgreSQL-only trusted-source full-text retrieval."""

    def __init__(self, repository: RagRepository | None = None) -> None:
        self._repository = repository or (RagRepository(settings.RAG_DATABASE_URL) if settings.RAG_DATABASE_URL else None)

    @property
    def available(self) -> bool:
        return bool(settings.RAG_ENABLED and self._repository)

    async def retrieve(self, query: str) -> RetrievalResult:
        normalized = query.strip()
        if not self.available or not normalized:
            return RetrievalResult(chunks=[], query=normalized, insufficient_evidence=True)
        chunks = await self._repository.search(query_text=normalized, top_k=settings.RAG_TOP_K)
        return RetrievalResult(chunks=chunks, query=normalized, insufficient_evidence=not chunks)

rag_retriever = PostgresRagRetriever()
