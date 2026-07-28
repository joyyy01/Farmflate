from __future__ import annotations

import asyncio
from unittest.mock import patch

from app.core.config import settings
from app.core.outbound_http import OutboundHttpClient


class _FakeAsyncClient:
    def __init__(self) -> None:
        self.requests: list[tuple[str, dict[str, object]]] = []
        self.closed = False

    async def post(self, url: str, **kwargs: object) -> object:
        self.requests.append((url, kwargs))
        return object()

    async def aclose(self) -> None:
        self.closed = True


def test_outbound_http_client_reuses_one_bounded_client_and_closes_it() -> None:
    created: list[tuple[_FakeAsyncClient, dict[str, object]]] = []

    def create_client(**kwargs: object) -> _FakeAsyncClient:
        client = _FakeAsyncClient()
        created.append((client, kwargs))
        return client

    async def exercise() -> tuple[OutboundHttpClient, _FakeAsyncClient, _FakeAsyncClient]:
        client = OutboundHttpClient()
        first, second = await asyncio.gather(client.start(), client.start())
        await client.post("https://example.test/first", json={"first": True})
        await client.post("https://example.test/second", json={"second": True})
        await client.close()
        return client, first, second

    with (
        patch.object(settings, "LLM_TIMEOUT_SECONDS", 7.5),
        patch("app.core.outbound_http.httpx.AsyncClient", side_effect=create_client),
    ):
        _, first, second = asyncio.run(exercise())

    shared_client, options = created[0]
    assert len(created) == 1
    assert first is second is shared_client
    assert shared_client.closed is True
    assert [request[0] for request in shared_client.requests] == [
        "https://example.test/first",
        "https://example.test/second",
    ]
    assert options["timeout"].connect == 7.5
