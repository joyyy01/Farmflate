import type { ChatRequest, ChatResponse, AgentTaskRequest, AgentTaskResponse } from '../types/chat';

const PYTHON_AI_URL = 'http://localhost:8000/api/v1';
const SPRING_BACKEND_URL = 'http://localhost:8080/api';

export const ApiService = {
  // Python FastAPI AI Chatbot Endpoint
  async sendChatMessage(payload: ChatRequest): Promise<ChatResponse> {
    try {
      const response = await fetch(`${PYTHON_AI_URL}/chat/`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) throw new Error('Failed to fetch from AI Server');
      return await response.json();
    } catch (err) {
      console.warn('Python AI Server offline, fallback mock used:', err);
      return {
        reply: `[Mock AI Response] Receptions for: "${payload.message}". (Ensure Python AI server is running on port 8000)`,
        status: 'mock'
      };
    }
  },

  // Python FastAPI AI Agent Execution Endpoint
  async runAgentTask(payload: AgentTaskRequest): Promise<AgentTaskResponse> {
    try {
      const response = await fetch(`${PYTHON_AI_URL}/agent/run`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) throw new Error('Agent execution failed');
      return await response.json();
    } catch (err) {
      console.warn('Python Agent offline:', err);
      return {
        task_id: 'mock_task',
        status: 'completed',
        result: `Mock Agent result for task: "${payload.task}"`,
        steps_taken: ['Plan generated', 'Executed simulated tools', 'Result finalized']
      };
    }
  },

  // Spring Boot Backend Health & Data Endpoint
  async checkBackendHealth(): Promise<{ status: string; message: string }> {
    try {
      const response = await fetch(`${SPRING_BACKEND_URL}/health`);
      if (!response.ok) throw new Error('Backend health check failed');
      return await response.json();
    } catch (err) {
      console.warn('Spring Backend offline:', err);
      return { status: 'offline', message: 'Spring Boot server offline on port 8080' };
    }
  }
};
