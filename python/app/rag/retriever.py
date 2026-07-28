from __future__ import annotations

from app.core.config import settings
from app.rag.embeddings import OpenAIEmbeddingClient
from app.rag.models import RetrievalResult
from app.rag.repository import RagRepository


class HybridRetriever:
    def __init__(
        self,
        repository: RagRepository | None = None,
        embeddings: OpenAIEmbeddingClient | None = None,
    ) -> None:
        self._repository = repository or (RagRepository(settings.RAG_DATABASE_URL) if settings.RAG_DATABASE_URL else None)
        self._embeddings = embeddings or OpenAIEmbeddingClient()

    @property
    def available(self) -> bool:
        return bool(settings.RAG_ENABLED and settings.RAG_DATABASE_URL and settings.OPENAI_API_KEY)

    async def retrieve(self, query: str, *, language: str | None = "ko") -> RetrievalResult:
        normalized = query.strip()
        if not self.available or not normalized:
            return RetrievalResult(chunks=[], query=normalized, insufficient_evidence=True)
        embedding = (await self._embeddings.embed([normalized]))[0]
        chunks = await self._repository.hybrid_search(
            query_text=normalized,
            embedding=embedding,
            top_k=settings.RAG_TOP_K,
            vector_candidates=settings.RAG_VECTOR_CANDIDATES,
            lexical_candidates=settings.RAG_LEXICAL_CANDIDATES,
            rrf_k=settings.RAG_RRF_K,
            language=language,
        )
        accepted = [chunk for chunk in chunks if chunk.score >= settings.RAG_MIN_SCORE]
        return RetrievalResult(chunks=accepted, query=normalized, insufficient_evidence=not accepted)


PostgresRagRetriever = HybridRetriever
rag_retriever = HybridRetriever()
