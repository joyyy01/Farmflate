export interface Message {
  id: string;
  sender: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

export interface ChatRequest {
  message: string;
  history?: { role: string; content: string }[];
}

export interface ChatResponse {
  reply: string;
  status: string;
}

export interface AgentTaskRequest {
  task: string;
}

export interface AgentTaskResponse {
  task_id: string;
  status: string;
  result: string;
  steps_taken: string[];
}
