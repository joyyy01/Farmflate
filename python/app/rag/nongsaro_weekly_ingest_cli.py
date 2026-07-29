from __future__ import annotations

import argparse
import asyncio
import json

from app.core.config import settings
from app.rag.ingest import OperatorIngestor
from app.rag.manifest import sha256_text
from app.rag.nongsaro_weekly import NongsaroWeeklyClient
from app.rag.repository import RagRepository


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Ingest approved Nongsaro weekly-farming PDFs.")
    parser.add_argument("--requested-by", required=True)
    parser.add_argument("--weeks", type=int, default=1)
    return parser.parse_args()


async def _run(args: argparse.Namespace) -> str:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for Nongsaro ingestion.")
    if not 1 <= args.weeks <= 12:
        raise ValueError("--weeks must be between 1 and 12.")
    documents = await NongsaroWeeklyClient(api_key=settings.NONGSARO_API_KEY).weekly_pdf_documents(limit=args.weeks)
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        ingested_documents: list[dict[str, str]] = []
        for document in documents:
            run_id = await OperatorIngestor(repository).ingest(
                source_url=document.canonical_url,
                source_version=document.source_version,
                external_id=f"nongsaro-weekly:{document.content_id}",
                title=document.title,
                language="ko",
                content=document.content,
                requested_by=args.requested_by,
                fetched_at=document.fetched_at,
            )
            ingested_documents.append({
                "ingestionRunId": str(run_id),
                "sourceVersion": document.source_version,
                "canonicalUrl": document.canonical_url,
                "contentSha256": sha256_text(document.content),
                "publishedAt": document.published_at.isoformat(),
                "fetchedAt": document.fetched_at.isoformat(),
            })
        return json.dumps({"documents": len(ingested_documents), "ingestedDocuments": ingested_documents}, ensure_ascii=False)
    finally:
        await repository.close()


def main() -> None:
    print(asyncio.run(_run(_arguments())))


if __name__ == "__main__":
    main()
