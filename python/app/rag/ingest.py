from __future__ import annotations

from datetime import datetime
from uuid import UUID

from app.core.config import settings
from app.rag.embeddings import OpenAIEmbeddingClient
from app.rag.ingestion import chunk_document
from app.rag.manifest import sha256_text
from app.rag.repository import ChunkInsert, RagRepository


class OperatorIngestor:
    """Explicit operator entry point; user chat requests cannot invoke ingestion."""

    def __init__(self, repository: RagRepository, embeddings: OpenAIEmbeddingClient | None = None) -> None:
        self._repository = repository
        self._embeddings = embeddings or OpenAIEmbeddingClient()

    async def ingest(
        self,
        *,
        source_url: str,
        source_version: str,
        title: str | None,
        language: str,
        content: str,
        requested_by: str,
        fetched_at: datetime,
    ) -> UUID:
        prepared = chunk_document(content, max_chars=settings.RAG_MAX_CHUNK_CHARS)
        if not prepared:
            raise ValueError("An ingested document must contain text.")
        vectors = await self._embeddings.embed([chunk.content for chunk in prepared])
        chunks = [
            ChunkInsert(ordinal=chunk.ordinal, content=chunk.content, content_sha256=chunk.content_sha256, embedding=vector)
            for chunk, vector in zip(prepared, vectors, strict=True)
        ]
        return await self._repository.ingest_document(
            source_url=source_url,
            source_version=source_version,
            title=title,
            language=language,
            content_sha256=sha256_text(content),
            fetched_at=fetched_at,
            requested_by=requested_by,
            embedding_model=settings.RAG_EMBEDDING_MODEL,
            embedding_version=settings.RAG_EMBEDDING_VERSION,
            chunks=chunks,
        )
