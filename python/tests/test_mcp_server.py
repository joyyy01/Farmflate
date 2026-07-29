from __future__ import annotations

import asyncio

from app.mcp.read_tools import McpReadTools
from app.mcp.server import build_mcp_server


def test_mcp_server_lists_only_two_read_only_tools() -> None:
    server = build_mcp_server(tools=McpReadTools(retriever=object(), weather_client=object()))

    assert [tool.name for tool in asyncio.run(server.list_tools())] == [
        "search_approved_agricultural_knowledge",
        "get_public_region_weather",
    ]
