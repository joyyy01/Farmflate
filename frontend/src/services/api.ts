import type { ChatRequest, ChatResponse } from '../types/chat';
import { normalizeRegionReport } from './reportLifecycle.ts';
import type {
  CreateFieldRequest,
  FieldProfile,
  RegionAnalysisRequest,
  RegionAnalysisStatus,
  RegionReport
} from '../types/report.ts';

const viteEnv = (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env ?? {};
const SPRING_BACKEND_URL = (viteEnv.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(/\/$/, '');

export type {
  CreateFieldRequest,
  FieldProfile,
  RegionAnalysisRequest,
  RegionAnalysisStatus,
  RegionReport
} from '../types/report.ts';

export interface RegionDto {
  sidoCode: string;
  sidoName?: string;
  sigunguCode?: string;
  sigunguName?: string;
}

export interface UserProfileDto {
  email: string;
  displayName: string;
  provider: string;
  role: string;
}

export interface HomeData {
  user?: { displayName?: string; email?: string; provider?: string };
  weather?: {
    status?: 'AVAILABLE' | 'UNAVAILABLE' | string;
    temperature?: number | null;
    minTemperature?: number | null;
    maxTemperature?: number | null;
    precipitationProbability?: number | null;
    condition?: 'SUNNY' | 'RAIN' | 'CLOUDY' | 'SNOW' | string | null;
    observedOrForecastAt?: string | null;
    isCached?: boolean | null;
  } | null;
  todayAction?: { title?: string | null; reason?: string | null; riskCode?: string | null } | null;
  latestRegionAnalysis?: {
    analysisId: string;
    regionName?: string | null;
    score?: number | null;
    topCrop?: { cropCode?: string | null; cropName?: string | null; score?: number | null; reason?: string | null } | null;
    analyzedAt?: string | null;
  } | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: unknown;
  readonly retryable: boolean;

  constructor(status: number, code: string, message: string, details: unknown = null, retryable = false) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.retryable = retryable;
  }
}

type JsonRecord = Record<string, unknown>;

const isRecord = (value: unknown): value is JsonRecord => typeof value === 'object' && value !== null && !Array.isArray(value);
const isString = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0;
const isNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const stringArray = (value: unknown): string[] => Array.isArray(value) ? value.filter((item): item is string => isString(item)) : [];
const normalizeFieldCondition = (value: unknown) => {
  const condition = isRecord(value) ? value : {};
  return {
    key: isString(condition.key) ? condition.key : null,
    label: isString(condition.label) ? condition.label : null,
    score: isNumber(condition.score) ? condition.score : null,
    status: isString(condition.status) ? condition.status : null,
    description: isString(condition.description) ? condition.description : null
  };
};
const normalizeFieldRisk = (value: unknown) => {
  const risk = isRecord(value) ? value : {};
  return {
    riskCode: isString(risk.riskCode) ? risk.riskCode : null,
    severity: isString(risk.severity) ? risk.severity : null,
    title: isString(risk.title) ? risk.title : null,
    description: isString(risk.description) ? risk.description : null,
    actions: stringArray(risk.actions)
  };
};

const getAuthHeaders = (): HeadersInit => {
  const token = typeof localStorage === 'undefined' ? null : (localStorage.getItem('jwtToken') || localStorage.getItem('token'));
  return {
    Accept: 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  };
};

const jsonHeaders = (): HeadersInit => ({
  ...getAuthHeaders(),
  'Content-Type': 'application/json'
});

const parseBody = async (response: Response): Promise<unknown> => {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
};

const toApiError = (response: Response, body: unknown): ApiError => {
  const record = isRecord(body) ? body : {};
  const error = isRecord(record.error) ? record.error : record;
  const code = isString(error.code) ? error.code : `HTTP_${response.status}`;
  const message = isString(error.message) ? error.message : `요청을 처리하지 못했습니다. (${response.status})`;
  const details = error.details ?? error.errors ?? null;
  const retryable = typeof error.retryable === 'boolean' ? error.retryable : response.status === 408 || response.status === 429 || response.status >= 500;
  return new ApiError(response.status, code, message, details, retryable);
};

const requestJson = async <T>(path: string, init?: RequestInit): Promise<T> => {
  let response: Response;
  try {
    response = await fetch(`${SPRING_BACKEND_URL}${path}`, init);
  } catch (error) {
    throw new ApiError(0, 'NETWORK_ERROR', error instanceof Error ? error.message : '네트워크 요청에 실패했습니다.', null, true);
  }

  const body = await parseBody(response);
  if (!response.ok) throw toApiError(response, body);
  return body as T;
};

const normalizeStatus = (input: unknown): RegionAnalysisStatus => {
  if (!isRecord(input) || !isString(input.analysisId) || !isString(input.status)) {
    throw new ApiError(200, 'MALFORMED_ANALYSIS_STATUS', '분석 상태 응답이 올바르지 않습니다.', input, false);
  }
  return {
    analysisId: input.analysisId,
    status: input.status,
    reused: typeof input.reused === 'boolean' ? input.reused : undefined,
    currentStep: isString(input.currentStep) ? input.currentStep : null,
    completedSteps: Array.isArray(input.completedSteps) ? input.completedSteps.filter((item): item is string => typeof item === 'string') : [],
    retryable: typeof input.retryable === 'boolean' ? input.retryable : undefined,
    errorCode: isString(input.errorCode) ? input.errorCode : null,
    errorMessage: isString(input.errorMessage) ? input.errorMessage : null
  };
};

const normalizeField = (input: unknown): FieldProfile => {
  if (!isRecord(input) || (typeof input.id !== 'string' && typeof input.id !== 'number') || !isString(input.fieldName)) {
    throw new ApiError(200, 'MALFORMED_FIELD', '밭 정보 응답이 올바르지 않습니다.', input, false);
  }
  const location = isRecord(input.location) ? input.location : null;
  const suitability = isRecord(input.suitabilityReport) ? input.suitabilityReport : null;
  const latestReport = isRecord(input.latestReport) ? input.latestReport : null;
  return {
    id: String(input.id),
    fieldName: input.fieldName,
    cropCode: isString(input.cropCode) ? input.cropCode : null,
    cropName: isString(input.cropName) ? input.cropName : null,
    location: location ? {
      addressLabel: isString(location.addressLabel) ? location.addressLabel : null,
      latitude: typeof location.latitude === 'number' ? location.latitude : null,
      longitude: typeof location.longitude === 'number' ? location.longitude : null,
      pnu: isString(location.pnu) ? location.pnu : null,
      spatialLevel: isString(location.spatialLevel) ? location.spatialLevel : null,
      precisionBadge: isString(location.precisionBadge) ? location.precisionBadge : null,
      evidenceLevel: isString(location.evidenceLevel) ? location.evidenceLevel : null
    } : null,
    cultivationMethod: isString(input.cultivationMethod) ? input.cultivationMethod : null,
    cultivationStartDate: isString(input.cultivationStartDate) ? input.cultivationStartDate : null,
    stage: isString(input.stage) ? input.stage : null,
    linkedRegionAnalysisId: isString(input.linkedRegionAnalysisId) ? input.linkedRegionAnalysisId : (isString(input.regionAnalysisId) ? input.regionAnalysisId : null),
    active: typeof input.active === 'boolean' ? input.active : null,
    createdAt: isString(input.createdAt) ? input.createdAt : null,
    updatedAt: isString(input.updatedAt) ? input.updatedAt : null,
    suitabilityReport: suitability ? {
      suitabilityScore: isNumber(suitability.suitabilityScore) ? suitability.suitabilityScore : null,
      grade: isString(suitability.grade) ? suitability.grade : null,
      summary: isString(suitability.summary) ? suitability.summary : null,
      analysisBasisDate: isString(suitability.analysisBasisDate) ? suitability.analysisBasisDate : null,
      regionAnalysisId: isString(suitability.regionAnalysisId) ? suitability.regionAnalysisId : null,
      conditions: Array.isArray(suitability.conditions) ? suitability.conditions.map(normalizeFieldCondition) : [],
      keyRisks: Array.isArray(suitability.keyRisks) ? suitability.keyRisks.map(normalizeFieldRisk) : [],
      prePlantChecklist: stringArray(suitability.prePlantChecklist),
      currentManagementPoints: stringArray(suitability.currentManagementPoints)
    } : null,
    latestReport: latestReport ? {
      id: isString(latestReport.id) ? latestReport.id : null,
      fieldId: isString(latestReport.fieldId) ? latestReport.fieldId : null,
      reportDate: isString(latestReport.reportDate) ? latestReport.reportDate : null,
      generatedAt: isString(latestReport.generatedAt) ? latestReport.generatedAt : null,
      generationReason: isString(latestReport.generationReason) ? latestReport.generationReason : null,
      suitabilityScore: isNumber(latestReport.suitabilityScore) ? latestReport.suitabilityScore : null,
      summary: isString(latestReport.summary) ? latestReport.summary : null,
      prioritizedActions: stringArray(latestReport.prioritizedActions),
      keyRisks: Array.isArray(latestReport.keyRisks) ? latestReport.keyRisks.map(normalizeFieldRisk) : [],
      conditions: Array.isArray(latestReport.conditions) ? latestReport.conditions.map(normalizeFieldCondition) : []
    } : null
  };
};

const sidosCache: { data: RegionDto[] | null } = { data: null };
const sigungusCacheMap = new Map<string, RegionDto[]>();

export const ApiService = {
  async getHome(): Promise<HomeData> {
    return requestJson<HomeData>('/home', { headers: getAuthHeaders() });
  },

  async updateUserProfile(payload: { nickname: string }): Promise<UserProfileDto> {
    return requestJson<UserProfileDto>('/users/me', {
      method: 'PUT',
      headers: jsonHeaders(),
      body: JSON.stringify(payload)
    });
  },

  async getSidos(): Promise<RegionDto[]> {
    if (sidosCache.data) return sidosCache.data;
    const response = await requestJson<unknown>('/regions/sidos', { headers: getAuthHeaders() });
    if (!Array.isArray(response)) throw new ApiError(200, 'MALFORMED_SIDOS', '시/도 목록 응답이 올바르지 않습니다.', response, false);
    const parsed = response.map(region => {
      if (!isRecord(region) || !isString(region.sidoCode) || !isString(region.sidoName)) {
        throw new ApiError(200, 'MALFORMED_SIDOS', '시/도 목록 항목이 올바르지 않습니다.', region, false);
      }
      return { sidoCode: region.sidoCode, sidoName: region.sidoName };
    });
    sidosCache.data = parsed;
    return parsed;
  },

  async getSigungus(sidoCode: string): Promise<RegionDto[]> {
    if (sigungusCacheMap.has(sidoCode)) {
      return sigungusCacheMap.get(sidoCode)!;
    }
    const response = await requestJson<unknown>(`/regions/sidos/${encodeURIComponent(sidoCode)}/sigungus`, { headers: getAuthHeaders() });
    if (!Array.isArray(response)) throw new ApiError(200, 'MALFORMED_SIGUNGUS', '시/군/구 목록 응답이 올바르지 않습니다.', response, false);
    const parsed = response.map(region => {
      if (!isRecord(region) || !isString(region.sidoCode) || !isString(region.sidoName) || !isString(region.sigunguCode) || !isString(region.sigunguName)) {
        throw new ApiError(200, 'MALFORMED_SIGUNGUS', '시/군/구 목록 항목이 올바르지 않습니다.', region, false);
      }
      return { sidoCode: region.sidoCode, sidoName: region.sidoName, sigunguCode: region.sigunguCode, sigunguName: region.sigunguName };
    });
    sigungusCacheMap.set(sidoCode, parsed);
    return parsed;
  },

  async createRegionAnalysis(payload: RegionAnalysisRequest): Promise<RegionAnalysisStatus> {
    const response = await requestJson<unknown>('/regions/analysis', {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(payload)
    });
    return normalizeStatus(response);
  },

  async getAnalysisStatus(analysisId: string): Promise<RegionAnalysisStatus> {
    const response = await requestJson<unknown>(`/regions/analysis/${encodeURIComponent(analysisId)}/status`, { headers: getAuthHeaders() });
    return normalizeStatus(response);
  },

  async getRegionReport(analysisId: string, knownStatus?: RegionAnalysisStatus['status']): Promise<RegionReport> {
    const response = await requestJson<unknown>(`/regions/reports/${encodeURIComponent(analysisId)}`, { headers: getAuthHeaders() });
    try {
      return normalizeRegionReport(response, knownStatus);
    } catch (error) {
      throw new ApiError(200, 'MALFORMED_REPORT', '검증 가능한 분석 리포트를 받지 못했습니다.', error instanceof Error ? error.message : response, false);
    }
  },

  async previewField(payload: CreateFieldRequest): Promise<FieldProfile> {
    const response = await requestJson<unknown>('/fields/preview', {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(payload)
    });
    return normalizeField(response);
  },

  async createField(payload: CreateFieldRequest): Promise<FieldProfile> {
    const response = await requestJson<unknown>('/fields', {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(payload)
    });
    return normalizeField(response);
  },

  async getFields(): Promise<FieldProfile[]> {
    const response = await requestJson<unknown>('/fields', { headers: getAuthHeaders() });
    if (!Array.isArray(response)) throw new ApiError(200, 'MALFORMED_FIELDS', '밭 목록 응답이 올바르지 않습니다.', response, false);
    const fields = response;
    return fields.map(normalizeField);
  },

  async getCommunityPosts(): Promise<unknown[]> {
    const response = await requestJson<unknown>('/community/posts', { headers: getAuthHeaders() });
    if (!Array.isArray(response)) throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 목록 응답이 올바르지 않습니다.', response, false);
    return response;
  },

  async createCommunityPost(payload: { category: string; tagLocation: string; title: string; content: string; author?: string; imageUrl?: string }): Promise<unknown> {
    return requestJson<unknown>('/community/posts', { method: 'POST', headers: jsonHeaders(), body: JSON.stringify(payload) });
  },

  async likeCommunityPost(postId: string): Promise<unknown> {
    return requestJson<unknown>(`/community/posts/${encodeURIComponent(postId)}/like`, { method: 'POST', headers: getAuthHeaders() });
  },

  async addCommunityComment(postId: string, payload: { author?: string; content: string }): Promise<unknown> {
    return requestJson<unknown>(`/community/posts/${encodeURIComponent(postId)}/comments`, { method: 'POST', headers: jsonHeaders(), body: JSON.stringify(payload) });
  },

  async submitInquiry(payload: { inquiryText: string; category?: string }): Promise<{ status: string; inquiryId: string }> {
    return requestJson<{ status: string; inquiryId: string }>('/users/inquiries', { method: 'POST', headers: jsonHeaders(), body: JSON.stringify(payload) });
  },

  async getUserInquiries(): Promise<Array<{ id: number; userEmail: string; inquiryText: string; category: string; status: string; createdAt: string }>> {
    const response = await requestJson<unknown>('/users/inquiries', { headers: getAuthHeaders() });
    return Array.isArray(response) ? response as Array<{ id: number; userEmail: string; inquiryText: string; category: string; status: string; createdAt: string }> : [];
  },

  async sendChatMessage(payload: ChatRequest): Promise<ChatResponse> {
    return requestJson<ChatResponse>('/assistant/messages', {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(payload)
    });
  }
};
