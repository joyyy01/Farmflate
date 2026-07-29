from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any

import uvicorn
from mcp.server import MCPServer
from starlette.datastructures import Headers
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

from app.core.config import settings
from app.integration.region_weather import HttpRegionWeatherClient
from app.mcp.access import McpAccessPolicy
from app.mcp.read_tools import McpReadTools
from app.rag.retriever import rag_retriever


class McpBearerMiddleware:
    def __init__(self, app: ASGIApp, access_policy: McpAccessPolicy) -> None:
        self._app = app
        self._access_policy = access_policy

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and not self._access_policy.authorize(Headers(scope=scope).get("authorization")):
            await JSONResponse({"error": "unauthorized"}, status_code=401)(scope, receive, send)
            return
        await self._app(scope, receive, send)


def build_mcp_server(*, tools: McpReadTools) -> MCPServer:
    server = MCPServer(
        name="Farmflate Read-only MCP",
        version=settings.VERSION,
        description="승인된 Farmflate 농업 지식과 공개 지역 예보를 읽는 도구입니다.",
    )

    @server.tool(
        name="search_approved_agricultural_knowledge",
        description="승인된 농업 지식에서 근거와 출처를 검색합니다.",
        structured_output=True,
    )
    async def search_approved_agricultural_knowledge(query: str, limit: int = 3) -> dict[str, Any]:
        return await tools.search_approved_agricultural_knowledge(query=query, limit=limit)

    @server.tool(
        name="get_public_region_weather",
        description="공개 지역 코드 기준의 최대 3일 예보를 조회합니다. 필지 정보는 받지 않습니다.",
        structured_output=True,
    )
    async def get_public_region_weather(sido_code: str, sigungu_code: str, days: int = 1) -> dict[str, Any]:
        return await tools.get_public_region_weather(
            sido_code=sido_code,
            sigungu_code=sigungu_code,
            days=days,
        )

    return server


def create_mcp_app() -> ASGIApp:
    if not settings.MCP_ENABLED:
        raise RuntimeError("MCP_ENABLED must be true before starting the MCP server.")
    settings.validate_runtime()
    tools = McpReadTools(
        retriever=rag_retriever,
        weather_client=HttpRegionWeatherClient(
            base_url=settings.MCP_SPRING_BASE_URL,
            token=settings.MCP_SPRING_INTERNAL_TOKEN,
        ),
    )
    app = build_mcp_server(tools=tools).streamable_http_app(
        streamable_http_path="/mcp",
        json_response=True,
        host=settings.MCP_HOST,
    )
    return McpBearerMiddleware(app, McpAccessPolicy(settings.MCP_ACCESS_TOKEN))


def run() -> None:
    uvicorn.run(create_mcp_app(), host=settings.MCP_HOST, port=settings.MCP_PORT)
