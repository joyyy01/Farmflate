from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, patch

import pytest

from app.agent.responses_client import ResponsesToolCallingClient
from app.core.config import settings


class _FakeResponse:
    def __init__(self, body: dict[str, object]) -> None:
        self._body = body

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return self._body


def _next_turn(body: dict[str, object]) -> tuple[ResponsesToolCallingClient, list[dict[str, object]], object]:
    payloads: list[dict[str, object]] = []

    async def post(_url: str, **kwargs: object) -> _FakeResponse:
        payloads.append(kwargs["json"])
        return _FakeResponse(body)

    with (
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
        patch("app.agent.responses_client.outbound_http_client.post", new_callable=AsyncMock, side_effect=post),
    ):
        client = ResponsesToolCallingClient()
        result = asyncio.run(client.next_turn(
            question="현재 밭 상태를 알려줘",
            history=[],
            instructions="test instructions",
            tool_definitions=[],
            tool_outputs=[],
        ))
    return client, payloads, result


def test_responses_client_rejects_multiple_function_calls() -> None:
    body = {
        "id": "resp-1",
        "output": [
            {"type": "function_call", "call_id": "call-1", "name": "read_authorized_context", "arguments": '{"section":"field"}'},
            {"type": "function_call", "call_id": "call-2", "name": "read_authorized_context", "arguments": '{"section":"risk"}'},
        ],
    }

    with pytest.raises(ValueError, match="exactly one function call"):
        _next_turn(body)


def test_responses_client_rejects_a_tool_call_without_a_response_id() -> None:
    body = {
        "output": [
            {"type": "function_call", "call_id": "call-1", "name": "read_authorized_context", "arguments": '{"section":"field"}'},
        ],
    }

    with pytest.raises(ValueError, match="response id"):
        _next_turn(body)


def test_responses_client_rejects_non_object_tool_arguments() -> None:
    body = {
        "id": "resp-1",
        "output": [
            {"type": "function_call", "call_id": "call-1", "name": "read_authorized_context", "arguments": "[]"},
        ],
    }

    with pytest.raises(ValueError, match="JSON object"):
        _next_turn(body)
