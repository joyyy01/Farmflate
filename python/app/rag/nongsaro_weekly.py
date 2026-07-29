from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from io import BytesIO
from urllib.parse import urlsplit
from xml.etree import ElementTree

import httpx
from pypdf import PdfReader


_LIST_URL = "https://api.nongsaro.go.kr/service/weekFarmInfo/weekFarmInfoList"
MAX_PDF_BYTES = 15 * 1024 * 1024
_MAX_PDF_PAGES = 120
_MAX_PDF_TEXT_CHARS = 500_000


@dataclass(frozen=True)
class NongsaroWeeklyDocument:
    content_id: str
    title: str
    published_at: datetime
    fetched_at: datetime
    source_version: str
    canonical_url: str
    content: str


class NongsaroWeeklyClient:
    """Read only official Nongsaro weekly PDFs from an operator-only CLI path."""

    def __init__(self, *, api_key: str, timeout_seconds: float = 20.0) -> None:
        if not api_key.strip():
            raise ValueError("NONGSARO_API_KEY must be configured.")
        self._api_key = api_key.strip()
        self._timeout_seconds = timeout_seconds

    async def latest_pdf_document(self) -> NongsaroWeeklyDocument:
        return (await self.weekly_pdf_documents(limit=1))[0]

    async def weekly_pdf_documents(self, *, limit: int) -> list[NongsaroWeeklyDocument]:
        if not 1 <= limit <= 12:
            raise ValueError("Nongsaro weekly document limit must be between 1 and 12.")
        # The source API controls attachment URLs. Do not follow an unexpected
        # redirect to a different network location, even though this is an
        # operator-only path rather than a user-facing request.
        async with httpx.AsyncClient(timeout=self._timeout_seconds, follow_redirects=False) as client:
            response = await client.get(_LIST_URL, params={"apiKey": self._api_key, "numOfRows": limit})
            response.raise_for_status()
            items = _weekly_items(response.text)[:limit]
            documents: list[NongsaroWeeklyDocument] = []
            for item in items:
                pdf_url = _pdf_url(item)
                pdf = await client.get(pdf_url)
                pdf.raise_for_status()
                _validate_pdf_size(pdf)
                content = _extract_pdf_text(pdf.content)
                if not content:
                    raise ValueError("Nongsaro weekly PDF did not contain extractable text.")
                documents.append(_document_from_item(
                    item,
                    pdf_url=pdf_url,
                    content=content,
                    fetched_at=datetime.now(UTC),
                ))
        return documents


def _document_from_item(
        item: ElementTree.Element,
        *,
        pdf_url: str,
        content: str,
        fetched_at: datetime,
) -> NongsaroWeeklyDocument:
    published_at = datetime.fromisoformat(_text(item, "regDt")).replace(tzinfo=UTC)
    content_id = _text(item, "cntntsNo")
    return NongsaroWeeklyDocument(
        content_id=content_id,
        title=_text(item, "subject"),
        published_at=published_at,
        fetched_at=fetched_at,
        source_version=f"nongsaro-weekly:{content_id}:{published_at.date().isoformat()}",
        canonical_url=pdf_url,
        content=content,
    )


def _latest_item(xml: str) -> ElementTree.Element:
    return _weekly_items(xml)[0]


def _weekly_items(xml: str) -> list[ElementTree.Element]:
    if "<!DOCTYPE" in xml.upper() or "<!ENTITY" in xml.upper():
        raise ValueError("Nongsaro API XML must not contain declarations or entities.")
    root = ElementTree.fromstring(xml)
    if (root.findtext("./header/resultCode") or "").strip() != "00":
        raise ValueError(f"Nongsaro API failed: {(root.findtext('./header/resultMsg') or 'unknown').strip()}")
    items = list(root.findall("./body/items/item"))
    if not items:
        raise ValueError("Nongsaro API returned no weekly-farming item.")
    return items


def _pdf_url(item: ElementTree.Element) -> str:
    names = _text(item, "fileName").split("|")
    urls = _text(item, "downUrlList").split("|")
    for name, url in zip(names, urls, strict=False):
        if name.lower().endswith(".pdf"):
            if _is_official_nongsaro_url(url):
                return url
            raise ValueError("Nongsaro weekly PDF URL must use an official Nongsaro host.")
    raise ValueError("Nongsaro weekly-farming item did not include a PDF attachment.")


def _is_official_nongsaro_url(url: str) -> bool:
    try:
        parsed = urlsplit(url)
        port = parsed.port
    except ValueError:
        return False
    hostname = (parsed.hostname or "").lower()
    return (
        parsed.scheme == "https"
        and not parsed.username
        and not parsed.password
        and port in {None, 443}
        and (hostname == "nongsaro.go.kr" or hostname.endswith(".nongsaro.go.kr"))
    )


def _validate_pdf_size(response: httpx.Response) -> None:
    content_length = response.headers.get("content-length")
    if content_length:
        try:
            declared_size = int(content_length)
        except ValueError as error:
            raise ValueError("Nongsaro weekly PDF returned an invalid Content-Length header.") from error
        if declared_size > MAX_PDF_BYTES:
            raise ValueError("Nongsaro weekly PDF exceeds the size limit.")
    if len(response.content) > MAX_PDF_BYTES:
        raise ValueError("Nongsaro weekly PDF exceeds the size limit.")


def _text(item: ElementTree.Element, name: str) -> str:
    value = item.findtext(name)
    if not value or not value.strip():
        raise ValueError(f"Nongsaro weekly-farming item is missing {name}.")
    return value.strip()


def _extract_pdf_text(payload: bytes) -> str:
    if len(payload) > MAX_PDF_BYTES:
        raise ValueError("Nongsaro weekly PDF exceeds the size limit.")
    reader = PdfReader(BytesIO(payload))
    if len(reader.pages) > _MAX_PDF_PAGES:
        raise ValueError("Nongsaro weekly PDF exceeds the page limit.")
    text = "\n".join(page.extract_text() or "" for page in reader.pages)
    normalized = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    if len(normalized) > _MAX_PDF_TEXT_CHARS:
        raise ValueError("Nongsaro weekly PDF exceeds the text limit.")
    return normalized
