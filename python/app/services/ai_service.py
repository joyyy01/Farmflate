import asyncio
from typing import AsyncGenerator
from app.schemas.chat import ChatRequest, ChatResponse, AgentTaskRequest, AgentTaskResponse

class AIService:
    """
    Service Layer responsible for interacting with LLM models,
    orchestrating AI Agents, and generating streaming responses.
    """
    
    async def process_chat(self, request: ChatRequest) -> ChatResponse:
        msg = request.message
        if "고창" in msg or "지역" in msg or "농사" in msg:
            reply_content = "전북 고창군은 최근 기온과 토양 pH(5.8) 조건이 감자 및 상추 재배에 매우 적합합니다. 다만 여름철 집중호우 가능성이 있으므로 배수로 점검을 권장합니다!"
        else:
            reply_content = f"Farmflate AI 도우미 답변: '{request.message}'에 대한 분석 결과를 기반으로 안내해 드립니다."
        return ChatResponse(reply=reply_content)

    async def stream_chat_response(self, request: ChatRequest) -> AsyncGenerator[str, None]:
        """
        Simulates Server-Sent Events (SSE) streaming output for real-time chatbot experience.
        """
        response_text = f"Farmflate AI 도우미: '{request.message}'에 대한 실시간 답변입니다."
        words = response_text.split()
        
        for word in words:
            yield f"data: {word} \n\n"
            await asyncio.sleep(0.1)

    async def execute_agent_task(self, request: AgentTaskRequest) -> AgentTaskResponse:
        # Autonomous AI Agent step execution simulation
        steps = [
            f"Analyzed region task: {request.task}",
            "Queried RDA Soil Chemistry & KMA Weather APIs",
            "Synthesized regional farming advice"
        ]
        return AgentTaskResponse(
            task_id="task_1001",
            status="completed",
            result=f"Successfully generated AI insight for: '{request.task}'",
            steps_taken=steps
        )

ai_service = AIService()
