from __future__ import annotations

from app.mcp.access import McpAccessPolicy


def test_access_policy_rejects_missing_or_wrong_bearer_token() -> None:
    policy = McpAccessPolicy("expected-token")

    assert policy.authorize(None) is False
    assert policy.authorize("Bearer wrong-token") is False
    assert policy.authorize("Bearer expected-token") is True
