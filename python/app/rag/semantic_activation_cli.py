from __future__ import annotations

import asyncio
import sys

from app.core.config import settings
from app.rag.repository import RagRepository


async def _run() -> str:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for semantic retrieval activation.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        enabled = await repository.enable_semantic_retrieval()
        if not enabled:
            raise RuntimeError("pgvector is not installed. Install it before semantic retrieval activation.")
        return "semantic retrieval activated"
    finally:
        await repository.close()


def main() -> None:
    try:
        print(asyncio.run(_run()))
    except RuntimeError as exc:
        print(f"semantic retrieval not activated: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc


if __name__ == "__main__":
    main()
