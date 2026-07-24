from fastapi import APIRouter, HTTPException
from app.schemas.chat import AgentTaskRequest, AgentTaskResponse
from app.services.ai_service import ai_service

router = APIRouter()

@router.post("/run", response_model=AgentTaskResponse)
async def run_agent_task(request: AgentTaskRequest):
    """
    Trigger autonomous AI Agent workflow task execution.
    """
    try:
        return await ai_service.execute_agent_task(request)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="에이전트 요청을 확인해 주세요.") from error
    except Exception as error:
        raise HTTPException(status_code=500, detail="AI 작업을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.") from error
