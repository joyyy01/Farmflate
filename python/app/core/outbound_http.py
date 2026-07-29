from __future__ import annotations

import asyncio
from typing import Any

import httpx

from app.core.config import settings


class OutboundHttpClient:
    """Process-lifetime HTTP client for bounded AI integration calls."""

    def __init__(self) -> None:
        self._client: httpx.AsyncClient | None = None
        self._lock = asyncio.Lock()

    async def start(self) -> httpx.AsyncClient:
        async with self._lock:
            if self._client is None:
                self._client = httpx.AsyncClient(
                    timeout=httpx.Timeout(settings.LLM_TIMEOUT_SECONDS),
                    limits=httpx.Limits(max_connections=20, max_keepalive_connections=10),
                )
            return self._client

    async def post(self, url: str, **kwargs: Any) -> httpx.Response:
        client = await self.start()
        return await client.post(url, **kwargs)

    async def get(self, url: str, **kwargs: Any) -> httpx.Response:
        client = await self.start()
        return await client.get(url, **kwargs)

    async def close(self) -> None:
        async with self._lock:
            client, self._client = self._client, None
        if client is not None:
            await client.aclose()


outbound_http_client = OutboundHttpClient()
