from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

import httpx

from app.core.config import settings
from app.core.outbound_http import outbound_http_client


class EmbeddingProvider(Protocol):
    """Creates vectors; PostgreSQL remains the storage and search engine."""

    model: str
    version: str
    dimensions: int

    async def embed_documents(self, documents: list[str]) -> list[tuple[float, ...]]: ...

    async def embed_query(self, query: str) -> tuple[float, ...]: ...


class EmbeddingUnavailable(RuntimeError):
    """The optional semantic lane cannot safely produce vectors."""


class OpenAIEmbeddingProvider:
    model: str
    version = "openai-v1"
    dimensions: int

    def __init__(self) -> None:
        self.model = settings.RAG_EMBEDDING_MODEL
        self.dimensions = settings.RAG_EMBEDDING_DIMENSIONS

    async def embed_documents(self, documents: list[str]) -> list[tuple[float, ...]]:
        return await self._embed(documents)

    async def embed_query(self, query: str) -> tuple[float, ...]:
        vectors = await self._embed([query])
        return vectors[0]

    async def _embed(self, texts: Sequence[str]) -> list[tuple[float, ...]]:
        if not texts:
            return []
        try:
            response = await outbound_http_client.post(
                f"{settings.OPENAI_BASE_URL.rstrip('/')}/embeddings",
                headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                json={
                    "model": self.model,
                    "input": list(texts),
                    "dimensions": self.dimensions,
                    "encoding_format": "float",
                },
            )
            response.raise_for_status()
            body = response.json()
            entries = sorted(body.get("data", []), key=lambda item: item.get("index", -1))
            vectors = [tuple(float(value) for value in item["embedding"]) for item in entries]
        except (httpx.HTTPError, KeyError, TypeError, ValueError) as error:
            raise EmbeddingUnavailable("Embedding response did not satisfy the contract.") from error
        if len(vectors) != len(texts) or any(len(vector) != self.dimensions for vector in vectors):
            raise EmbeddingUnavailable("Embedding response dimensions did not satisfy the contract.")
        return vectors


def configured_embedding_provider() -> EmbeddingProvider | None:
    if not settings.RAG_HYBRID_ENABLED or not settings.OPENAI_API_KEY:
        return None
    return OpenAIEmbeddingProvider()
