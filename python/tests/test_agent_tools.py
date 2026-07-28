from __future__ import annotations

import asyncio

from app.agent.tools import AgentToolExecutor
from app.schemas.chat import FactPackage


def test_agent_tool_executor_rejects_any_tool_outside_the_read_only_allow_list() -> None:
    result = asyncio.run(
        AgentToolExecutor().execute(
            name="update_field",
            arguments={"fieldId": "another-users-field", "status": "DONE"},
            fact_package=FactPackage(requestId="request-1", question="밭 상태를 바꿔줘"),
        )
    )

    assert result.status == "tool_not_allowed"
    assert result.citations == []
