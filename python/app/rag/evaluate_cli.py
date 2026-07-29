from __future__ import annotations

import asyncio
import argparse

from app.core.config import settings
from app.rag.embeddings import configured_embedding_provider
from app.rag.evaluation import RagEvaluator
from app.rag.repository import RagRepository
from app.rag.retriever import PostgresRagRetriever


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate one fixed PostgreSQL RAG dataset version.")
    parser.add_argument("--dataset-key", required=True)
    parser.add_argument("--dataset-version", required=True)
    parser.add_argument("--mode", required=True, choices=("lexical", "hybrid"))
    parser.add_argument("--requested-by", required=True)
    return parser.parse_args()


async def _run(args: argparse.Namespace) -> str:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for evaluation.")
    if args.mode == "hybrid" and configured_embedding_provider() is None:
        raise RuntimeError("Hybrid evaluation requires the explicit hybrid setting and an embedding provider.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        corpus_snapshot = await repository.evaluation_corpus_snapshot(
            dataset_key=args.dataset_key,
            dataset_version=args.dataset_version,
        )
        run_id = await repository.start_evaluation_run(
            dataset_key=args.dataset_key,
            dataset_version=args.dataset_version,
            retrieval_mode=args.mode,
            retrieval_config={
                "topK": settings.RAG_TOP_K,
                "lexicalCandidateLimit": settings.RAG_LEXICAL_CANDIDATE_LIMIT,
                "semanticCandidateLimit": settings.RAG_SEMANTIC_CANDIDATE_LIMIT,
                "rrfK": settings.RAG_RRF_K,
            },
            requested_by=args.requested_by,
            corpus_snapshot=corpus_snapshot,
        )
        try:
            retriever = PostgresRagRetriever(
                repository,
                use_configured_embedding_provider=args.mode == "hybrid",
            )
            summary = await RagEvaluator(repository, retriever).evaluate_all(
                dataset_key=args.dataset_key,
                dataset_version=args.dataset_version,
                evaluation_run_id=run_id,
            )
            await repository.complete_evaluation_run(run_id=run_id, case_count=summary.total)
        except Exception as error:
            await repository.complete_evaluation_run(
                run_id=run_id,
                case_count=0,
                failure_reason=type(error).__name__,
            )
            raise
        return (
            f"run_id={run_id} total={summary.total} passed={summary.passed} "
            f"failed={summary.failed} errors={summary.errors}"
        )
    finally:
        await repository.close()


def main() -> None:
    print(asyncio.run(_run(_arguments())))


if __name__ == "__main__":
    main()
