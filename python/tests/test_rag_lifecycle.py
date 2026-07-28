from __future__ import annotations

import asyncio
from unittest.mock import patch

from app.rag.repository import RagRepository


class _FakePool:
    async def close(self) -> None:
        return None


def test_repository_creates_one_pool_when_first_two_requests_arrive_together() -> None:
    created: list[_FakePool] = []

    async def create_pool(*_: object, **__: object) -> _FakePool:
        await asyncio.sleep(0)
        pool = _FakePool()
        created.append(pool)
        return pool

    repository = RagRepository("postgresql://rag")

    async def open_two_pools() -> tuple[_FakePool, _FakePool]:
        return await asyncio.gather(repository._connection_pool(), repository._connection_pool())

    with patch("app.rag.repository.asyncpg.create_pool", side_effect=create_pool):
        first, second = asyncio.run(open_two_pools())

    assert first is second
    assert len(created) == 1
