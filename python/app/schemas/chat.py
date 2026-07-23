from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any

class ChatMessage(BaseModel):
    role: str = Field(..., description="Role of the speaker (user, assistant, system)")
    content: str = Field(..., description="Content of the message")

class ChatRequest(BaseModel):
    message: str = Field(..., description="User message prompt")
    history: Optional[List[ChatMessage]] = Field(default=[], description="Previous conversation history")
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)

class ChatResponse(BaseModel):
    reply: str
    status: str = "success"

class AgentTaskRequest(BaseModel):
    task: str = Field(..., description="Goal/Task for the AI Agent to perform")
    context: Optional[Dict[str, Any]] = None

class AgentTaskResponse(BaseModel):
    task_id: str
    status: str
    result: str
    steps_taken: List[str]
