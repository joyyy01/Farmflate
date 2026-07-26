export interface GroundingSource {
  title: string;
  detail?: string | null;
  observed_at?: string | null;
  observedAt?: string | null;
  sourceUrl?: string | null;
}

export interface Message {
  id: string;
  sender: 'user' | 'assistant';
  content: string;
  timestamp: string;
  sources?: GroundingSource[];
  grounded?: boolean;
}

export interface ChatRequest {
  message: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
  context?: {
    regionAnalysisId?: string | null;
    fieldId?: string | null;
    reportDate?: string | null;
    route?: string;
    visibleData?: VisibleDataRef[];
  };
}

export type ChatRoute = 'home' | 'field_dashboard' | 'community' | 'mypage' | 'region_report';

export type VisibleDataSection = 'summary' | 'climate' | 'soil' | 'hazard' | 'crop' | 'field';

export interface VisibleDataRef {
  key: string;
  label: string;
  section: VisibleDataSection;
  /** Matching hint only. The server deliberately discards this value. */
  displayValue?: string;
}

export interface AIChatContext {
  route: ChatRoute;
  regionAnalysisId: string | null;
  fieldId: string | null;
  reportDate: string | null;
  visibleData?: VisibleDataRef[];
}

export interface StructuredAnswer {
  answer: string;
  basisType: string;
  usedFactIds: string[];
  usedSourceIds: string[];
  mentionedNumbers: number[];
  mentionedCrops: string[];
  mentionedRisks: string[];
  safetyNotice: string | null;
}

export interface ChatResponse {
  requestId: string;
  status: string;
  answer: StructuredAnswer;
  sources: GroundingSource[];
}

export interface AgentTaskRequest {
  message: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
  context?: {
    regionAnalysisId?: string | null;
    fieldId?: string | null;
    route?: string;
  };
}

export interface AgentTaskResponse {
  requestId: string;
  status: string;
  answer: StructuredAnswer;
  sources: GroundingSource[];
}
