from fastapi import APIRouter
from app.api.v1.endpoints import chat, agent

api_router = APIRouter()
api_router.include_router(chat.router, prefix="/chat", tags=["Chat"])
api_router.include_router(agent.router, prefix="/agent", tags=["Agent"])
