export type TerminalAnalysisStatus = 'COMPLETED' | 'PARTIAL';
export type AnalysisStatus = 'PENDING' | 'PROCESSING' | TerminalAnalysisStatus | 'FAILED';
export type DataMode = 'LIVE' | 'AUTO' | string;

export interface RegionIdentity {
  sidoCode: string;
  sidoName: string;
  sigunguCode: string;
  sigunguName: string;
}

export interface LocationRequest {
  address?: string;
  latitude?: number;
  longitude?: number;
  pnu?: string;
  parcelSoilTestRef?: string;
}

export interface LocationResolution {
  addressLabel?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  pnu?: string | null;
  spatialLevel?: string | null;
  precisionBadge?: string | null;
  evidenceLevel?: string | null;
  sourceRefs?: SourceReference[];
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

export interface SafeWorkWindow {
  start?: string | null;
  end?: string | null;
  label?: string | null;
  reason?: string | null;
  confidence?: number | null;
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
  dataMode: DataMode | null;
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
  topRisks: RiskEvent[];
  safeWorkWindows: SafeWorkWindow[];
  prioritizedActions: PrioritizedAction[];
  tips: PrioritizedAction[];
  sources: SourceReference[];
  missingMetrics: string[];
}

export interface RegionAnalysisRequest extends RegionIdentity {
  location?: LocationRequest;
  idempotencyKey: string;
  forceRefresh?: boolean;
}

export interface RegionAnalysisStatus {
  analysisId: string;
  status: AnalysisStatus | string;
  reused?: boolean;
  currentStep?: string | null;
  completedSteps?: string[];
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
  prioritizedActions: string[];
  keyRisks: FieldRisk[];
  conditions: FieldCondition[];
}

export interface CreateFieldRequest {
  fieldName: string;
  cropCode?: string;
  cropName: string;
  location?: LocationRequest;
  cultivationMethod: string;
  cultivationStartDate: string;
  stage?: string;
  regionAnalysisId: string;
}
