from __future__ import annotations

import asyncio
import json
from unittest.mock import AsyncMock, patch

import pytest

from app.agent.responses_client import FINAL_ANSWER_SCHEMA, ResponseContractError, ResponsesToolCallingClient
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
    assert payloads[0]["max_output_tokens"] == 800
    assert payloads[1]["previous_response_id"] == "resp-tool"


def test_final_answer_schema_requires_sectioned_cited_response_structure() -> None:
    answer_block_schema = FINAL_ANSWER_SCHEMA["properties"]["answer_blocks"]["items"]
    answer_schema = answer_block_schema["properties"]["text"]

    assert answer_block_schema["properties"]["section"]["enum"] == ["judgment", "evidence", "actions"]
    assert "section" in answer_block_schema["required"]
    assert answer_schema["minLength"] == 1
    assert FINAL_ANSWER_SCHEMA["properties"]["answer_blocks"]["minItems"] == 0
    assert answer_block_schema["properties"]["citation_ids"]["minItems"] == 0


def test_responses_client_accepts_an_empty_block_list_for_needs_context() -> None:
    _, _, result = _next_turn({
        "id": "resp-needs-context",
        "output_text": json.dumps({
            "answer_blocks": [],
            "status": "needs_context",
            "safety_notice": None,
        }),
        "output": [],
    })

    assert result.status == "needs_context"
    assert result.citation_ids == []


def test_responses_client_wraps_invalid_structured_json_as_a_contract_error() -> None:
    with pytest.raises(ResponseContractError, match="structured answer"):
        _next_turn({
            "id": "resp-invalid-json",
            "output_text": "{invalid-json",
            "output": [],
        })


def test_responses_client_renders_sectioned_cited_answer_blocks_deterministically() -> None:
    judgment = "장마철에는 병해충 발생 조건을 먼저 확인해야 합니다. 습도가 높고 잎이 오래 젖어 있으면 병해 확산 가능성이 커질 수 있습니다."
    evidence = "승인된 주간 농사정보에는 강우 뒤 포장 습도와 작물 잎의 이상 증상을 함께 점검하라고 안내합니다."
    actions = "1. 포장 배수와 고인 물을 확인하세요.\n2. 잎 뒷면과 줄기의 반점·변색을 관찰하세요.\n3. 이상 증상이 보이면 작물과 지역을 추가로 알려 주세요."

    _, _, result = _next_turn({
        "id": "resp-blocks",
        "output_text": json.dumps({
            "answer_blocks": [
                {"section": "actions", "text": actions, "citation_ids": ["rag:chunk-1"]},
                {"section": "judgment", "text": judgment, "citation_ids": ["rag:chunk-1"]},
                {"section": "evidence", "text": evidence, "citation_ids": ["rag:chunk-1"]},
            ],
            "status": "completed",
            "safety_notice": None,
        }),
        "output": [],
    })

    assert result.answer == f"핵심 판단\n{judgment}\n\n근거\n{evidence}\n\n지금 할 일\n{actions}"
    assert result.claims == [
        {"text": actions, "citation_ids": ["rag:chunk-1"]},
        {"text": judgment, "citation_ids": ["rag:chunk-1"]},
        {"text": evidence, "citation_ids": ["rag:chunk-1"]},
    ]
    assert result.citation_ids == ["rag:chunk-1"]


def test_responses_client_rejects_completed_answer_without_all_visible_sections() -> None:
    with pytest.raises(ValueError, match="all visible sections"):
        _next_turn({
            "id": "resp-incomplete-sections",
            "output_text": json.dumps({
                "answer_blocks": [{
                    "section": "actions",
                    "text": "1. 포장 배수를 확인하세요.",
                    "citation_ids": ["rag:chunk-1"],
                }],
                "status": "completed",
                "safety_notice": None,
            }),
            "output": [],
        })


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
