from __future__ import annotations

import hmac


class McpAccessPolicy:
    def __init__(self, expected_token: str) -> None:
        self._expected_token = expected_token

    def authorize(self, authorization: str | None) -> bool:
        if not authorization or not authorization.startswith("Bearer "):
            return False
        provided_token = authorization.removeprefix("Bearer ")
        return bool(self._expected_token) and hmac.compare_digest(self._expected_token, provided_token)
