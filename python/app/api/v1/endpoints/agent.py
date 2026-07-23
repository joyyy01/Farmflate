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
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
