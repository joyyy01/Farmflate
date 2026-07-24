from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from app.schemas.chat import ChatRequest, ChatResponse
from app.services.ai_service import ai_service

router = APIRouter()

@router.post("/", response_model=ChatResponse)
async def chat_endpoint(request: ChatRequest):
    """
    Standard HTTP POST chat completion endpoint.
    """
    try:
        return await ai_service.process_chat(request)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="채팅 요청을 확인해 주세요.") from error
    except Exception as error:
        raise HTTPException(status_code=500, detail="AI 설명을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.") from error

@router.post("/stream")
async def chat_stream_endpoint(request: ChatRequest):
    """
    Server-Sent Events (SSE) streaming endpoint for real-time response rendering.
    """
    return StreamingResponse(ai_service.stream_chat_response(request), media_type="text/event-stream")
