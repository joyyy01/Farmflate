from __future__ import annotations

from typing import Any, Protocol

from app.integration.region_weather import RegionWeatherClient
from app.rag.models import RetrievalResult


class KnowledgeRetriever(Protocol):
    async def retrieve(self, query: str, *, language: str | None = "ko", request_id: str | None = None) -> RetrievalResult: ...


class McpReadTools:
    def __init__(self, *, retriever: KnowledgeRetriever, weather_client: RegionWeatherClient) -> None:
        self._retriever = retriever
        self._weather_client = weather_client

    async def search_approved_agricultural_knowledge(self, *, query: str, limit: int = 3) -> dict[str, Any]:
        normalized_query = query.strip()
        if not normalized_query or len(normalized_query) > 300:
            return {"status": "invalid_request", "sources": []}
        if limit not in (1, 2, 3):
            return {"status": "invalid_request", "sources": []}

        retrieval = await self._retriever.retrieve(normalized_query, language="ko")
        if retrieval.insufficient_evidence:
            return {"status": "needs_context", "sources": []}

        return {
            "status": "ok",
            "sources": [
                {
                    "title": chunk.title or chunk.source_name,
                    "url": chunk.canonical_url,
                    "excerpt": chunk.content[:600],
                }
                for chunk in retrieval.chunks[:limit]
            ],
        }

    async def get_public_region_weather(
            self,
            *,
            sido_code: str,
            sigungu_code: str,
            days: int = 1,
    ) -> dict[str, Any]:
        try:
            snapshot = await self._weather_client.read(
                sido_code=sido_code,
                sigungu_code=sigungu_code,
                days=days,
            )
        except ValueError:
            return {"status": "invalid_request", "days": []}

        return snapshot.model_dump(by_alias=True)
