export type TerminalAnalysisStatus = 'COMPLETED' | 'PARTIAL';
export type AnalysisStatus = 'PENDING' | 'PROCESSING' | TerminalAnalysisStatus | 'FAILED';

export interface RegionIdentity {
  sidoCode: string;
  sidoName: string;
  sigunguCode: string;
  sigunguName: string;
}

export interface LocationResolution {
  addressLabel?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  pnu?: string | null;
  spatialLevel?: string | null;
  precisionBadge?: string | null;
  evidenceLevel?: string | null;
  sourceRefs?: string[];
  transformations?: string[];
  validationFlags?: string[];
}

export interface SourceReference {
  provider?: string | null;
  service?: string | null;
  sourceUrl?: string | null;
  sourceRecordId?: string | null;
  dataDate?: string | null;
  measurementOrIssueAt?: string | null;
  spatialLevel?: string | null;
  precisionBadge?: string | null;
  evidenceLevel?: string | null;
  isCached?: boolean | null;
  isFallback?: boolean | null;
  fallbackReason?: string | null;
  transformations?: string[];
}

export interface DataConfidence {
  score: number | null;
  level: string | null;
  message?: string | null;
  range?: { min: number | null; max: number | null } | null;
}

export interface RecommendedCrop {
  cropCode?: string | null;
  cropName?: string | null;
  score?: number | null;
  rank?: number | null;
  positiveReasons: string[];
  cautionReason?: string | null;
  category?: string | null;
  iconUrl?: string | null;
}

export interface CropDecision {
  cropCode?: string | null;
  cropName?: string | null;
  score?: number | null;
  calculable?: boolean | null;
  notCalculableReason?: string | null;
  soilSuitabilityScore?: number | null;
  soilPhScore?: number | null;
  seasonalTemperatureScore?: number | null;
  positiveReasons: string[];
  cautionReason?: string | null;
}

export interface RiskEvent {
  rank?: number | null;
  riskCode?: string | null;
  severity?: string | null;
  level?: string | null;
  title?: string | null;
  description?: string | null;
  period?: { start?: string | null; end?: string | null } | null;
  affectedCrops: string[];
  actions: string[];
  causalChain: string[];
  criticalCap?: number | null;
  remainingRisk?: number | null;
  source?: SourceReference | null;
  evidenceRefs: SourceReference[];
}

export interface PrioritizedAction {
  rank?: number | null;
  title?: string | null;
  reason?: string | null;
  leadTime?: string | null;
  stage?: string | null;
  tipCode?: string | null;
  sourceName?: string | null;
  sourceUrl?: string | null;
  sourceRefs: SourceReference[];
}

export interface RegionReport {
  analysisId: string;
  status: TerminalAnalysisStatus;
  analyzedAt?: string | null;
  region: RegionIdentity;
  location?: LocationResolution | null;
  baseFitness: number | null;
  seasonReadiness: number | null;
  dataConfidence: DataConfidence;
  /** Compatibility-only legacy value; it is never used as the decision surface. */
  regionScore?: number | null;
  summary?: string | null;
  components?: {
    climate?: { score?: number | null; grade?: string | null } | null;
    soil?: { score?: number | null; grade?: string | null } | null;
    hazard?: { safetyScore?: number | null; grade?: string | null } | null;
    cultivation?: { score?: number | null; grade?: string | null } | null;
  } | null;
  environmentFeatures: string[];
  recommendedCrops: RecommendedCrop[];
  cropResults: CropDecision[];
  topRisks: RiskEvent[];
  prioritizedActions: PrioritizedAction[];
  tips: PrioritizedAction[];
  sources: SourceReference[];
  missingMetrics: string[];
}

export interface RegionAnalysisRequest extends RegionIdentity {
  idempotencyKey: string;
  forceRefresh?: boolean;
  /** 'FIELD_LINKED' when this analysis only backs one field's suitability
      scoring; omit (defaults to 'PRIMARY' server-side) for the user's
      representative region set from Home/My Page. */
  purpose?: 'PRIMARY' | 'FIELD_LINKED';
}

export interface RegionAnalysisStatus {
  analysisId: string;
  status: AnalysisStatus | string;
  reused?: boolean;
  currentStep?: string | null;
  completedSteps?: string[];
  currentStepCode?: string | null;
  completedStepCodes?: string[];
  retryable?: boolean;
  errorCode?: string | null;
  errorMessage?: string | null;
}

export interface FieldProfile {
  id: string;
  fieldName: string;
  cropCode?: string | null;
  cropName?: string | null;
  location?: LocationResolution | null;
  cultivationMethod?: string | null;
  cultivationStartDate?: string | null;
  stage?: string | null;
  linkedRegionAnalysisId?: string | null;
  active?: boolean | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  suitabilityReport?: FieldSuitabilityReport | null;
  latestReport?: LatestFieldReport | null;
  cultivationDay?: number | null;
  dailyStatus?: 'STABLE' | 'CAUTION' | 'NEEDS_CHECK' | null;
  dailyStatusLabel?: string | null;
  dailyHeadline?: string | null;
  dailyReportDate?: string | null;
  dailyAlerts?: FieldAlert[] | null;
}

export interface FieldCondition {
  key?: string | null;
  label?: string | null;
  score?: number | null;
  status?: string | null;
  description?: string | null;
}

export interface FieldRisk {
  riskCode?: string | null;
  severity?: string | null;
  title?: string | null;
  description?: string | null;
  actions: string[];
}

export interface FieldAlert {
  key: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  title: string;
  description: string;
}

export interface FieldSuitabilityReport {
  suitabilityScore?: number | null;
  grade?: string | null;
  summary?: string | null;
  analysisBasisDate?: string | null;
  regionAnalysisId?: string | null;
  conditions: FieldCondition[];
  keyRisks: FieldRisk[];
  prePlantChecklist: string[];
  currentManagementPoints: string[];
}

export interface LatestFieldReport {
  id?: string | null;
  fieldId?: string | null;
  reportDate?: string | null;
  generatedAt?: string | null;
  generationReason?: string | null;
  suitabilityScore?: number | null;
  summary?: string | null;
  headline?: string | null;
  headlineDescription?: string | null;
  prioritizedActions: string[];
  keyRisks: FieldRisk[];
  conditions: FieldCondition[];
}

export interface CreateFieldRequest {
  fieldName: string;
  cropCode?: string;
  cropName: string;
  cultivationMethod: string;
  cultivationStartDate: string;
  stage?: string;
  regionAnalysisId: string;
}

export interface FieldSuitabilityPreview {
  fieldName: string;
  cropCode?: string | null;
  cropName?: string | null;
  cultivationMethod?: string | null;
  cultivationStartDate?: string | null;
  stage?: string | null;
  regionAnalysisId?: string | null;
  suitabilityReport?: FieldSuitabilityReport | null;
}

export type FieldDailyStatus = 'STABLE' | 'CAUTION' | 'NEEDS_CHECK';
export type FieldTaskBadge = 'MORNING_RECOMMENDED' | 'CHECK_ANYTIME';
export type FieldLogCategory =
  | 'WATERING'
  | 'FERTILIZING'
  | 'LEAF_CHECK'
  | 'PEST_CONTROL'
  | 'OTHER';

export interface FieldActivityLog {
  id: string;
  fieldId: string;
  category: FieldLogCategory;
  categoryLabel: string;
  note: string;
  loggedAt: string;
}

export interface FieldHistoryItem {
  date: string;
  status: FieldDailyStatus | null;
  statusLabel: string;
  logLabels: string[];
  reportAvailable: boolean;
  keyMetric: string | null;
  managementSummary: string | null;
}

export interface FieldDashboardResponse {
  field: {
    id: string;
    fieldName: string;
    cropCode: string | null;
    cropName: string | null;
    regionName: string;
    cultivationStartDate: string | null;
    cultivationDay: number | null;
    stage: string | null;
  };
  report: {
    id: string;
    reportDate: string;
    generatedAt: string;
    generationReason: string;
    status: FieldDailyStatus;
    headline: string;
    headlineDescription: string;
    historical: boolean;
    taskCountBeforeAcknowledgement: number;
    /** 0-100 종합 상태 점수; null when weather data was unavailable that day. */
    statusScore: number | null;
    /** '적정' | '주의' | '위험' | '확인 필요' (null-score fallback). */
    statusScoreZone: string;
  };
  weather: {
    status: 'AVAILABLE' | 'UNAVAILABLE';
    currentTemperature: number | null;
    minTemperature: number | null;
    maxTemperature: number | null;
    precipitationProbability: number | null;
    rainfallMm: number | null;
    humidity: number | null;
    windSpeed: number | null;
    condition: string | null;
  };
  soil?: {
    available: boolean;
    ph: number | null;
    ec: number | null;
  };
  tasks: Array<{
    key: string;
    title: string;
    description: string;
    badge: FieldTaskBadge;
    acknowledged: boolean;
  }>;
  alerts: Array<{
    key: string;
    severity: 'LOW' | 'MEDIUM' | 'HIGH';
    title: string;
    description: string;
  }>;
  reasoning: {
    summary: string;
    points: string[];
  };
  todayLogs: FieldActivityLog[];
  history: FieldHistoryItem[];
}

export interface TaskAcknowledgement {
  taskKey: string;
  acknowledged: boolean;
  acknowledgedAt: string;
}
