from __future__ import annotations

import argparse
import asyncio
import json

from app.core.config import settings
from app.rag.auto_evaluation import PublicContentQuestionGenerator
from app.rag.repository import RagRepository


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create silver RAG evaluation cases from an approved public source.")
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--dataset-key", required=True)
    parser.add_argument("--dataset-version", required=True)
    parser.add_argument("--questions-per-chunk", type=int, default=3)
    return parser.parse_args()


async def _run(args: argparse.Namespace) -> int:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for automatic evaluation seeding.")
    if not 1 <= args.questions_per_chunk <= 5:
        raise ValueError("questions-per-chunk must be between 1 and 5.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        if not await repository.embedding_egress_allowed(canonical_url=args.source_url):
            raise PermissionError("Automatic LLM generation requires explicit public-content egress approval.")
        chunks = await repository.current_chunks_for_source(canonical_url=args.source_url)
        generator = PublicContentQuestionGenerator(api_key=settings.OPENAI_API_KEY, model=settings.OPENAI_MODEL)
        cases = []
        for chunk_id, content in chunks:
            generated = await generator.generate(chunk_id=chunk_id, content=content, count=args.questions_per_chunk)
            cases.extend((item.case_key, item.query_text, item.expected_chunk_id) for item in generated)
        return await repository.upsert_auto_evaluation_cases(
            dataset_key=args.dataset_key,
            dataset_version=args.dataset_version,
            cases=cases,
        )
    finally:
        await repository.close()


def main() -> None:
    args = _arguments()
    print(json.dumps({"created": asyncio.run(_run(args)), "origin": "AUTO_GENERATED"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
