from datetime import UTC, datetime

import pytest

from app.rag.nongsaro_weekly import (
    MAX_PDF_BYTES,
    _document_from_item,
    _extract_pdf_text,
    _latest_item,
    _pdf_url,
    _text,
    _weekly_items,
)


def test_weekly_farming_api_selects_only_the_pdf_attachment() -> None:
    item = _latest_item("""
        <response><header><resultCode>00</resultCode><resultMsg>OK</resultMsg></header><body><items><item>
        <cntntsNo>123</cntntsNo><subject>주간농사정보</subject><regDt>2026-07-29</regDt>
        <fileName>weekly.hwpx|weekly.hwp|weekly.pdf</fileName>
        <downUrlList>https://www.nongsaro.go.kr/a|https://www.nongsaro.go.kr/b|https://www.nongsaro.go.kr/c</downUrlList>
        </item></items></body></response>
    """)

    assert _pdf_url(item) == "https://www.nongsaro.go.kr/c"


def test_weekly_document_keeps_the_verified_pdf_url_and_actual_fetch_time() -> None:
    item = _latest_item("""
        <response><header><resultCode>00</resultCode></header><body><items><item>
        <cntntsNo>123</cntntsNo><subject>주간농사정보</subject><regDt>2026-07-29</regDt>
        <fileName>weekly.pdf</fileName><downUrlList>https://www.nongsaro.go.kr/files/weekly.pdf</downUrlList>
        </item></items></body></response>
    """)
    fetched_at = datetime(2026, 7, 30, 9, 15, tzinfo=UTC)

    document = _document_from_item(
        item,
        pdf_url=_pdf_url(item),
        content="검증된 주간 농사 정보",
        fetched_at=fetched_at,
    )

    assert document.canonical_url == "https://www.nongsaro.go.kr/files/weekly.pdf"
    assert document.published_at == datetime(2026, 7, 29, tzinfo=UTC)
    assert document.fetched_at == fetched_at


def test_weekly_farming_api_rejects_a_pdf_url_outside_the_official_nongsaro_host() -> None:
    item = _latest_item("""
        <response><header><resultCode>00</resultCode></header><body><items><item>
        <cntntsNo>123</cntntsNo><subject>주간농사정보</subject><regDt>2026-07-29</regDt>
        <fileName>weekly.pdf</fileName><downUrlList>https://example.test/weekly.pdf</downUrlList>
        </item></items></body></response>
    """)

    with pytest.raises(ValueError, match="official Nongsaro host"):
        _pdf_url(item)


def test_weekly_farming_api_rejects_an_oversized_pdf_before_parsing() -> None:
    with pytest.raises(ValueError, match="size limit"):
        _extract_pdf_text(b"0" * (MAX_PDF_BYTES + 1))


def test_weekly_farming_api_rejects_an_unsuccessful_response() -> None:
    with pytest.raises(ValueError, match="Nongsaro API failed"):
        _latest_item("<response><header><resultCode>99</resultCode><resultMsg>denied</resultMsg></header></response>")


def test_weekly_farming_api_keeps_each_requested_publication_in_newest_first_order() -> None:
    items = _weekly_items("""
        <response><header><resultCode>00</resultCode></header><body><items>
        <item><cntntsNo>3</cntntsNo><subject>세 번째</subject><regDt>2026-07-29</regDt></item>
        <item><cntntsNo>2</cntntsNo><subject>두 번째</subject><regDt>2026-07-22</regDt></item>
        </items></body></response>
    """)

    assert [_text(item, "cntntsNo") for item in items] == ["3", "2"]
