from fastapi import APIRouter, Depends, HTTPException

from app.core.auth import verify_internal_key
from app.schemas.chat import (
    AgentRunRequest,
    AgentRunResponse,
    AgentTaskRequest,
    AgentTaskResponse,
    FieldGuidanceRequest,
    FieldGuidanceResponse,
)
from app.services.ai_service import ai_service

router = APIRouter(dependencies=[Depends(verify_internal_key)])


@router.post("/run", response_model=AgentRunResponse)
async def run_agent(request: AgentRunRequest):
    try:
        return await ai_service.run_agent(request)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="에이전트 요청을 확인해 주세요.") from error
    except Exception as error:
        raise HTTPException(status_code=500, detail="AI 작업을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.") from error


@router.post("/task", response_model=AgentTaskResponse)
async def run_agent_task(request: AgentTaskRequest):
    try:
        return await ai_service.execute_agent_task(request)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="에이전트 요청을 확인해 주세요.") from error
    except Exception as error:
        raise HTTPException(status_code=500, detail="AI 작업을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.") from error


@router.post("/field-guidance", response_model=FieldGuidanceResponse)
async def build_field_guidance(request: FieldGuidanceRequest):
    try:
        return await ai_service.generate_field_guidance(request)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="밭 리포트 정보를 확인해 주세요.") from error
    except Exception as error:
        raise HTTPException(status_code=500, detail="밭 리포트 안내를 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.") from error
