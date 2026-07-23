import type { ChatRequest, ChatResponse, AgentTaskRequest, AgentTaskResponse } from '../types/chat';

const PYTHON_AI_URL = 'http://localhost:8000/api/v1';
const SPRING_BACKEND_URL = 'http://localhost:8080/api';

const getAuthHeaders = (): HeadersInit => {
  const token = localStorage.getItem('jwtToken') || localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  };
};

const handleAuthError = (res: Response) => {
  if (res.status === 401 || res.status === 403 || res.redirected || !res.ok) {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('token');
    if (window.location.search !== '?view=landing') {
      window.location.href = '/?view=landing';
    }
  }
};

export interface RegionDto {
  sidoCode: string;
  sidoName?: string;
  sigunguCode?: string;
  sigunguName?: string;
}

export interface HomeData {
  user: { displayName: string };
  weather?: {
    status: string;
    temperature?: number;
    minTemperature?: number;
    maxTemperature?: number;
    precipitationProbability?: number;
    condition?: string;
    observedOrForecastAt?: string;
    isCached?: boolean;
  };
  todayAction?: {
    title: string;
    reason: string;
    riskCode: string;
  };
  latestRegionAnalysis?: {
    analysisId: string;
    regionName: string;
    score: number;
    topCrop?: {
      cropCode: string;
      cropName: string;
      score: number;
      reason: string;
    };
    analyzedAt: string;
  };
  farms: any[];
}

export interface RegionAnalysisStatus {
  analysisId: string;
  status: string;
  completedSteps: string[];
  currentStep: string;
  retryable: boolean;
}

export interface RegionReport {
  analysisId: string;
  region: RegionDto;
  regionScore: number;
  grade: string;
  summary: string;
  confidence: {
    grade: string;
    score: number;
    message: string;
  };
  components: {
    climate: { score: number; grade: string };
    soil: { score: number; grade: string };
    hazard: { safetyScore: number; grade: string };
    cultivation: { score: number; grade: string };
  };
  recommendedCrops: {
    cropCode: string;
    cropName: string;
    score: number;
    rank: number;
    positiveReasons: string[];
    cautionReason: string;
  }[];
  topRisks: {
    rank: number;
    riskCode: string;
    level: string;
    title: string;
    description: string;
    period?: { start: string; end: string };
    affectedCrops: string[];
    actions: string[];
    source?: { provider: string; service: string; sourceUrl: string; dataDate: string };
  }[];
  tips: {
    rank: number;
    tipCode: string;
    title: string;
    summary: string;
    sourceType: string;
    sourceName: string;
    sourceUrl: string;
    actionLabel: string;
    dataDate: string;
  }[];
  sources: { provider: string; service: string; sourceUrl: string; dataDate: string }[];
  analyzedAt: string;
  dataMode: string;
}

export const ApiService = {
  // Region APIs
  async getHome(): Promise<HomeData> {
    const res = await fetch(`${SPRING_BACKEND_URL}/home`, { headers: getAuthHeaders() });
    handleAuthError(res);
    if (!res.ok) throw new Error('Failed to fetch home data');
    return res.json();
  },

  async getSidos(): Promise<RegionDto[]> {
    const res = await fetch(`${SPRING_BACKEND_URL}/regions/sidos`, { headers: getAuthHeaders() });
    handleAuthError(res);
    if (!res.ok) throw new Error('Failed to fetch sidos');
    return res.json();
  },

  async getSigungus(sidoCode: string): Promise<RegionDto[]> {
    const res = await fetch(`${SPRING_BACKEND_URL}/regions/sidos/${sidoCode}/sigungus`, { headers: getAuthHeaders() });
    handleAuthError(res);
    if (!res.ok) throw new Error('Failed to fetch sigungus');
    return res.json();
  },

  async createRegionAnalysis(payload: { sidoCode: string; sigunguCode: string; forceRefresh?: boolean; idempotencyKey?: string }): Promise<RegionAnalysisStatus> {
    const res = await fetch(`${SPRING_BACKEND_URL}/region-analyses`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(payload)
    });
    handleAuthError(res);
    if (!res.ok) throw new Error('Failed to create region analysis');
    return res.json();
  },

  async getAnalysisStatus(analysisId: string): Promise<RegionAnalysisStatus> {
    const res = await fetch(`${SPRING_BACKEND_URL}/region-analyses/${analysisId}/status`, { headers: getAuthHeaders() });
    handleAuthError(res);
    if (!res.ok) throw new Error('Failed to fetch analysis status');
    return res.json();
  },

  async getRegionReport(analysisId: string): Promise<RegionReport> {
    const res = await fetch(`${SPRING_BACKEND_URL}/region-analyses/${analysisId}`, { headers: getAuthHeaders() });
    handleAuthError(res);
    if (!res.ok) throw new Error('Failed to fetch region report');
    return res.json();
  },

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
        reply: `[AI 도우미] 선택하신 지역(고창군)은 감자 및 상추 재배에 호조를 보입니다!`,
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
      return { status: 'offline', message: 'Spring Boot server offline on port 8080' };
    }
  }
};
