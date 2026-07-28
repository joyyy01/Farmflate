from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


@dataclass(frozen=True)
class ApprovedSource:
    canonical_url: str
    publisher: str
    source_name: str
    content_policy: str


def load_approved_sources(manifest_path: Path) -> list[ApprovedSource]:
    """Load a checked-in operator manifest; requests never supply source URLs."""
    raw = manifest_path.read_bytes()
    payload = json.loads(raw)
    sources = payload.get("sources", []) if isinstance(payload, dict) else []
    approved: list[ApprovedSource] = []
    for entry in sources:
        if not isinstance(entry, dict) or entry.get("approvalStatus") != "APPROVED":
            continue
        url = str(entry.get("canonicalUrl", "")).strip()
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise ValueError(f"Approved source must use a canonical HTTPS URL: {url}")
        approved.append(ApprovedSource(
            canonical_url=url,
            publisher=str(entry.get("publisher", "")).strip(),
            source_name=str(entry.get("sourceName", "")).strip(),
            content_policy=str(entry.get("contentPolicy", "")).strip(),
        ))
    if not approved:
        raise ValueError("Manifest does not contain an approved source.")
    return approved


def sha256_text(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()
