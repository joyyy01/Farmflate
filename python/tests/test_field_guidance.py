from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, patch

from app.core.config import settings
from app.core.outbound_http import outbound_http_client
from app.schemas.chat import FieldGuidanceRequest
from app.services.field_guidance import FieldGuidanceService


class _Response:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return {
            "choices": [
                {
                    "message": {
                        "content": '{"reasoningSummary":"감자에 집중 강수가 예상돼 배수로 확인을 먼저 안내했어요."}'
                    }
                }
            ]
        }


class _DirectClient:
    def __init__(self, **_: object) -> None:
        pass

    async def __aenter__(self) -> "_DirectClient":
        return self

    async def __aexit__(self, *_: object) -> None:
        return None

    async def post(self, *_: object, **__: object) -> _Response:
        return _Response()


def test_field_guidance_uses_the_process_lifetime_outbound_http_client() -> None:
    post = AsyncMock(return_value=_Response())
    request = FieldGuidanceRequest(
        facts={
            "cropName": "감자",
            "alerts": [{"title": "집중 강수"}],
            "tasks": [{"key": "drainage", "title": "배수로 확인", "description": "배수로를 점검하세요."}],
        }
    )

    with (
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
        patch.object(outbound_http_client, "post", post),
        patch("app.services.field_guidance.httpx.AsyncClient", _DirectClient),
    ):
        response = asyncio.run(FieldGuidanceService().generate(request))

    assert response.reasoningSummary
    post.assert_awaited_once()
    assert post.await_args.args[0].endswith("/chat/completions")
