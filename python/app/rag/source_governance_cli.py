from __future__ import annotations

import argparse
import asyncio
import json
from dataclasses import asdict
from datetime import datetime

from app.core.config import settings
from app.rag.repository import RagRepository


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Register and review approved PostgreSQL RAG sources.")
    commands = parser.add_subparsers(dest="command", required=True)

    register = commands.add_parser("register", help="Create a PENDING source without retrieval access.")
    register.add_argument("--url", required=True)
    register.add_argument("--publisher", required=True)
    register.add_argument("--name", required=True)
    register.add_argument("--policy", default="PUBLIC")
    register.add_argument("--actor", required=True)
    register.add_argument("--reason", required=True)
    register.add_argument("--expires-at", help="ISO-8601 timestamp with timezone, if the source expires.")

    review = commands.add_parser("review", help="Approve, reject, or revoke a registered source.")
    review.add_argument("--url", required=True)
    review.add_argument("--action", required=True, choices=("APPROVED", "REJECTED", "REVOKED"))
    review.add_argument("--actor", required=True)
    review.add_argument("--reason", required=True)

    egress = commands.add_parser("embedding-egress", help="Allow or revoke external embedding for an approved source.")
    egress.add_argument("--url", required=True)
    egress.add_argument("--allowed", required=True, choices=("true", "false"))
    egress.add_argument("--actor", required=True)
    egress.add_argument("--reason", required=True)
    return parser.parse_args()


def _expires_at(value: str | None) -> datetime | None:
    if value is None:
        return None
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        raise ValueError("--expires-at must include a timezone offset.")
    return parsed


async def _run(args: argparse.Namespace) -> dict[str, str]:
    if not settings.RAG_DATABASE_URL:
        raise RuntimeError("RAG_DATABASE_URL must be configured for source governance.")
    repository = RagRepository(settings.RAG_DATABASE_URL)
    try:
        if args.command == "register":
            result = await repository.register_source(
                canonical_url=args.url,
                publisher=args.publisher,
                source_name=args.name,
                content_policy=args.policy,
                actor=args.actor,
                reason=args.reason,
                expires_at=_expires_at(args.expires_at),
            )
        elif args.command == "review":
            result = await repository.review_source(
                canonical_url=args.url,
                action=args.action,
                actor=args.actor,
                reason=args.reason,
            )
        else:
            result = await repository.set_embedding_egress_policy(
                canonical_url=args.url,
                allowed=args.allowed == "true",
                actor=args.actor,
                reason=args.reason,
            )
        return asdict(result)
    finally:
        await repository.close()


def main() -> None:
    print(json.dumps(asyncio.run(_run(_arguments())), ensure_ascii=False))


if __name__ == "__main__":
    main()
