from __future__ import annotations

import asyncio
import logging

import pytest
from pydantic import ValidationError

from app.agent.runner import GroundedAgent
from app.schemas.chat import ChatRequest, FactPackage


def test_fact_package_rejects_context_deeper_than_the_model_input_budget() -> None:
    context = {"a": {"b": {"c": {"d": {"e": "too deep"}}}}}

    with pytest.raises(ValidationError, match="context"):
        FactPackage(requestId="budget-depth", question="밭 상태를 알려줘", context=context)


def test_chat_request_rejects_context_deeper_than_the_model_input_budget() -> None:
    context = {"a": {"b": {"c": {"d": {"e": "too deep"}}}}}

    with pytest.raises(ValidationError, match="context"):
        ChatRequest(message="밭 상태를 알려줘", context=context)


def test_agent_records_safe_failure_metadata_without_logging_exception_detail(caplog: pytest.LogCaptureFixture) -> None:
    class RaisingModel:
        async def next_turn(self, **_: object) -> object:
            raise RuntimeError("secret-like upstream detail")

    caplog.set_level(logging.WARNING, logger="app.agent.runner")

    result = asyncio.run(
        GroundedAgent(model=RaisingModel()).run(FactPackage(requestId="safe-log", question="밭 상태를 알려줘"))
    )

    record = next(record for record in caplog.records if record.getMessage() == "agent_execution_failed")
    assert result.status == "failed"
    assert result.answer == "AI 도우미를 준비하지 못했습니다. 잠시 후 다시 시도해 주세요."
    assert record.request_id == "safe-log"
    assert record.stage == "model_or_tool"
    assert record.error_type == "RuntimeError"
    assert "secret-like" not in record.getMessage()
