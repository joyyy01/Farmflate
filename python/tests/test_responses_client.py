from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, patch

import pytest

from app.agent.responses_client import FINAL_ANSWER_SCHEMA, ResponsesToolCallingClient
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


def test_responses_client_keeps_state_for_function_call_continuation() -> None:
    payloads: list[dict[str, object]] = []
    responses = [
        {
            "id": "resp-tool",
            "output": [
                {
                    "type": "function_call",
                    "call_id": "call-1",
                    "name": "read_authorized_context",
                    "arguments": '{"section":"risk"}',
                }
            ],
        },
        {
            "id": "resp-final",
            "output_text": '{"answer":"verified answer","claims":[],"citation_ids":[],"status":"needs_context","safety_notice":null}',
            "output": [],
        },
    ]

    async def post(_url: str, **kwargs: object) -> _FakeResponse:
        payloads.append(kwargs["json"])
        return _FakeResponse(responses.pop(0))

    with (
        patch.object(settings, "OPENAI_API_KEY", "test-key"),
        patch("app.agent.responses_client.outbound_http_client.post", new_callable=AsyncMock, side_effect=post),
    ):
        client = ResponsesToolCallingClient()
        tool_call = asyncio.run(client.next_turn(
            question="test question",
            history=[],
            instructions="test instructions",
            tool_definitions=[],
            tool_outputs=[],
        ))
        asyncio.run(client.next_turn(
            question="test question",
            history=[],
            instructions="test instructions",
            tool_definitions=[],
            tool_outputs=[{"type": "function_call_output", "call_id": tool_call.call_id, "output": "{}"}],
        ))

    assert "store" not in payloads[0]
    assert payloads[1]["previous_response_id"] == "resp-tool"


def test_final_answer_schema_requires_a_detailed_korean_response_structure() -> None:
    answer_schema = FINAL_ANSWER_SCHEMA["properties"]["answer"]

    assert answer_schema["minLength"] >= 240
    assert "핵심 판단" in answer_schema["pattern"]
    assert "근거" in answer_schema["pattern"]
    assert "지금 할 일" in answer_schema["pattern"]


def test_responses_client_normalizes_an_incomplete_needs_context_draft() -> None:
    _, _, result = _next_turn({
        "id": "resp-needs-context",
        "output_text": '{"status":"needs_context","safety_notice":null}',
        "output": [],
    })

    assert result.status == "needs_context"
    assert result.answer
    assert result.claims == []
    assert result.citation_ids == []
