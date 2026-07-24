export interface GroundingSource {
  title: string;
  detail?: string | null;
  observed_at?: string | null;
}

export interface Message {
  id: string;
  sender: 'user' | 'assistant';
  content: string;
  timestamp: string;
  sources?: GroundingSource[];
  grounded?: boolean;
}

export interface ChatPageContext {
  region?: string;
  selected_crop?: string;
  report?: unknown;
  home?: unknown;
  fields?: unknown[];
}

export interface ChatRequest {
  message: string;
  history?: { role: 'user' | 'assistant'; content: string }[];
  context?: ChatPageContext;
  temperature?: number;
}

export interface ChatResponse {
  reply: string;
  status: 'grounded' | 'needs_context';
  sources: GroundingSource[];
  used_context: string[];
  agent_steps: string[];
}

export interface AgentTaskRequest {
  task: string;
  context?: ChatPageContext;
  history?: { role: 'user' | 'assistant'; content: string }[];
}

export interface AgentTaskResponse {
  task_id: string;
  status: 'completed' | 'needs_context';
  result: string;
  steps_taken: string[];
  sources: GroundingSource[];
}
