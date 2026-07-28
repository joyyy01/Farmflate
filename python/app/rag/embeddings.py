from __future__ import annotations

from collections.abc import Sequence

import httpx

from app.core.config import settings


class OpenAIEmbeddingClient:
    """Single, versioned embedding boundary for PostgreSQL pgvector retrieval."""

    async def embed(self, texts: Sequence[str]) -> list[list[float]]:
        if not texts:
            return []
        if not settings.OPENAI_API_KEY:
            raise RuntimeError("OPENAI_API_KEY is required for hybrid RAG embeddings.")
        async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
            response = await client.post(
                f"{settings.OPENAI_BASE_URL.rstrip('/')}/embeddings",
                headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                json={
                    "model": settings.RAG_EMBEDDING_MODEL,
                    "input": list(texts),
                    "dimensions": settings.RAG_EMBEDDING_DIMENSIONS,
                },
            )
            response.raise_for_status()
        values = response.json().get("data", [])
        vectors = [item.get("embedding") for item in values]
        if len(vectors) != len(texts) or any(not isinstance(vector, list) for vector in vectors):
            raise ValueError("Embedding response does not match requested inputs.")
        if any(len(vector) != settings.RAG_EMBEDDING_DIMENSIONS for vector in vectors):
            raise ValueError("Embedding dimensions do not match rag.chunk.embedding.")
        return [[float(value) for value in vector] for vector in vectors]
