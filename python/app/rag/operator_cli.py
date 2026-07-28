from __future__ import annotations

import argparse
import asyncio
from datetime import UTC, datetime
from pathlib import Path

from app.core.config import settings
from app.rag.ingest import OperatorIngestor
from app.rag.repository import RagRepository


def _parse_fetched_at(value: str | None) -> datetime:
    if value is None:
        return datetime.now(UTC)
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        raise argparse.ArgumentTypeError("--fetched-at must include a UTC offset.")
    return parsed


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Ingest one approved RAG source from a local text file.")
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--source-version", required=True)
    parser.add_argument("--content-file", required=True, type=Path)
    parser.add_argument("--requested-by", required=True)
    parser.add_argument("--title")
    parser.add_argument("--language", default="ko")
    parser.add_argument("--fetched-at", type=_parse_fetched_at)
    return parser.parse_args()


async def _run(args: argparse.Namespace) -> str:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for operator ingestion.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        run_id = await OperatorIngestor(repository).ingest(
            source_url=args.source_url,
            source_version=args.source_version,
            title=args.title,
            language=args.language,
            content=args.content_file.read_text(encoding="utf-8"),
            requested_by=args.requested_by,
            fetched_at=args.fetched_at or datetime.now(UTC),
        )
        return str(run_id)
    finally:
        await repository.close()


def main() -> None:
    args = _arguments()
    print(asyncio.run(_run(args)))


if __name__ == "__main__":
    main()
