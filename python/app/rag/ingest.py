from __future__ import annotations

from datetime import datetime
from uuid import UUID

from app.core.config import settings
from app.rag.embeddings import EmbeddingProvider, EmbeddingUnavailable, configured_embedding_provider
from app.rag.ingestion import chunk_document
from app.rag.manifest import sha256_text
from app.rag.repository import ChunkInsert, RagRepository


class OperatorIngestor:
    """Explicit operator entry point; user chat requests cannot invoke ingestion."""

    def __init__(
        self,
        repository: RagRepository,
        embedding_provider: EmbeddingProvider | None = None,
    ) -> None:
        self._repository = repository
        self._embedding_provider = embedding_provider if embedding_provider is not None else configured_embedding_provider()

    async def ingest(
        self,
        *,
        source_url: str,
        source_version: str,
        external_id: str | None = None,
        title: str | None,
        language: str,
        content: str,
        requested_by: str,
        fetched_at: datetime,
    ) -> UUID:
        prepared = chunk_document(content, max_chars=settings.RAG_MAX_CHUNK_CHARS)
        if not prepared:
            raise ValueError("An ingested document must contain text.")
        chunks = [
            ChunkInsert(ordinal=chunk.ordinal, content=chunk.content, content_sha256=chunk.content_sha256)
            for chunk in prepared
        ]
        embedding_status = "NOT_REQUESTED"
        embedding_failure_reason: str | None = None
        embedding_model: str | None = None
        embedding_dimensions: int | None = None
        if self._embedding_provider is not None:
            embedding_model = self._embedding_provider.model
            embedding_dimensions = self._embedding_provider.dimensions
            if not await self._repository.embedding_egress_allowed(canonical_url=source_url):
                embedding_status = "POLICY_DENIED"
                embedding_failure_reason = "Source policy does not permit external embedding."
            elif not await self._repository.semantic_search_available():
                embedding_status = "UNAVAILABLE"
                embedding_failure_reason = "pgvector is not available."
            else:
                try:
                    vectors = await self._embedding_provider.embed_documents([chunk.content for chunk in chunks])
                    if len(vectors) != len(chunks):
                        raise EmbeddingUnavailable("Embedding provider returned an incomplete document batch.")
                    chunks = [
                        ChunkInsert(
                            ordinal=chunk.ordinal,
                            content=chunk.content,
                            content_sha256=chunk.content_sha256,
                            embedding=vector,
                            embedding_model=self._embedding_provider.model,
                            embedding_version=self._embedding_provider.version,
                            embedding_dimensions=self._embedding_provider.dimensions,
                        )
                        for chunk, vector in zip(chunks, vectors, strict=True)
                    ]
                    embedding_status = "READY"
                except EmbeddingUnavailable as error:
                    embedding_status = "UNAVAILABLE"
                    embedding_failure_reason = str(error)[:1000]
        return await self._repository.ingest_document(
            source_url=source_url,
            source_version=source_version,
            external_id=external_id,
            title=title,
            language=language,
            content_sha256=sha256_text(content),
            fetched_at=fetched_at,
            requested_by=requested_by,
            chunks=chunks,
            embedding_status=embedding_status,
            embedding_model=embedding_model,
            embedding_dimensions=embedding_dimensions,
            embedding_failure_reason=embedding_failure_reason,
        )
