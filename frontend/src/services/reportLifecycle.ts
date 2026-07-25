import type {
  AnalysisStatus,
  DataConfidence,
  LocationResolution,
  PrioritizedAction,
  RecommendedCrop,
  RegionAnalysisStatus,
  RegionIdentity,
  RegionReport,
  RiskEvent,
  SafeWorkWindow,
  SourceReference,
  TerminalAnalysisStatus
} from '../types/report.ts';

export type AnalysisState =
  | { kind: 'IDLE' }
  | { kind: 'SUBMITTING' }
  | { kind: 'POLLING'; analysisId: string; currentStep?: string | null; completedSteps: string[] }
  | { kind: 'COMPLETED'; report: RegionReport }
  | { kind: 'PARTIAL'; report: RegionReport }
  | { kind: 'ERROR'; message: string; code?: string | null; retryable: boolean; pendingAction?: 'ANALYSIS' | 'FIELD' }
  | { kind: 'UNAUTHORIZED'; message: string; pendingAction: 'ANALYSIS' | 'FIELD' };

type UnknownRecord = Record<string, unknown>;

const isRecord = (value: unknown): value is UnknownRecord => typeof value === 'object' && value !== null && !Array.isArray(value);
const asString = (value: unknown): string | null => typeof value === 'string' && value.trim() ? value : null;
const asNumber = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;
const asBoolean = (value: unknown): boolean | null => typeof value === 'boolean' ? value : null;
const asStringArray = (value: unknown): string[] => Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
const asRecordArray = (value: unknown): UnknownRecord[] => Array.isArray(value) ? value.filter(isRecord) : [];

const normalizeSource = (input: unknown): SourceReference => {
  const source = isRecord(input) ? input : {};
  return {
    provider: asString(source.provider),
    service: asString(source.service),
    sourceUrl: asString(source.sourceUrl),
    sourceRecordId: asString(source.sourceRecordId),
    dataDate: asString(source.dataDate),
    measurementOrIssueAt: asString(source.measurementOrIssueAt),
    spatialLevel: asString(source.spatialLevel),
    precisionBadge: asString(source.precisionBadge),
    evidenceLevel: asString(source.evidenceLevel),
    isCached: asBoolean(source.isCached),
    isFallback: asBoolean(source.isFallback),
    fallbackReason: asString(source.fallbackReason),
    transformations: asStringArray(source.transformations)
  };
};

const normalizeLocation = (input: unknown): LocationResolution | null => {
  if (!isRecord(input)) return null;
  return {
    addressLabel: asString(input.addressLabel),
    latitude: asNumber(input.latitude),
    longitude: asNumber(input.longitude),
    pnu: asString(input.pnu),
    spatialLevel: asString(input.spatialLevel),
    precisionBadge: asString(input.precisionBadge),
    evidenceLevel: asString(input.evidenceLevel),
    sourceRefs: asStringArray(input.sourceRefs),
    transformations: asStringArray(input.transformations),
    validationFlags: asStringArray(input.validationFlags)
  };
};

const normalizeConfidence = (input: unknown): DataConfidence => {
  const confidence = isRecord(input) ? input : {};
  const range = isRecord(confidence.range) ? confidence.range : null;
  return {
    score: asNumber(confidence.score),
    level: asString(confidence.level) ?? asString(confidence.grade),
    message: asString(confidence.message),
    range: range ? { min: asNumber(range.min), max: asNumber(range.max) } : null
  };
};

const normalizeCrop = (input: UnknownRecord): RecommendedCrop => ({
  cropCode: asString(input.cropCode),
  cropName: asString(input.cropName),
  score: asNumber(input.score) ?? asNumber(input.baseFitness),
  rank: asNumber(input.rank),
  positiveReasons: asStringArray(input.positiveReasons),
  cautionReason: asString(input.cautionReason),
  category: asString(input.category),
  iconUrl: asString(input.iconUrl)
});

const normalizeRisk = (input: UnknownRecord): RiskEvent => {
  const period = isRecord(input.period) ? input.period : null;
  return {
    rank: asNumber(input.rank),
    riskCode: asString(input.riskCode),
    severity: asString(input.severity),
    level: asString(input.level),
    title: asString(input.title),
    description: asString(input.description),
    period: period ? { start: asString(period.start), end: asString(period.end) } : null,
    affectedCrops: asStringArray(input.affectedCrops),
    actions: asStringArray(input.actions),
    causalChain: asStringArray(input.causalChain),
    criticalCap: asNumber(input.criticalCap),
    remainingRisk: asNumber(input.remainingRisk),
    source: isRecord(input.source) ? normalizeSource(input.source) : null,
    evidenceRefs: asRecordArray(input.evidenceRefs).map(normalizeSource)
  };
};

const normalizeSafeWindow = (input: UnknownRecord): SafeWorkWindow => ({
  start: asString(input.start),
  end: asString(input.end),
  label: asString(input.label),
  reason: asString(input.reason),
  confidence: asNumber(input.confidence)
});

const normalizeAction = (input: UnknownRecord): PrioritizedAction => ({
  rank: asNumber(input.rank),
  title: asString(input.title),
  reason: asString(input.reason) ?? asString(input.summary),
  leadTime: asString(input.leadTime),
  stage: asString(input.stage),
  tipCode: asString(input.tipCode),
  sourceName: asString(input.sourceName),
  sourceUrl: asString(input.sourceUrl),
  sourceRefs: asRecordArray(input.sourceRefs).map(normalizeSource)
});

const normalizeComponents = (input: unknown): RegionReport['components'] => {
  if (!isRecord(input)) return null;
  const simple = (value: unknown) => {
    const item = isRecord(value) ? value : {};
    return { score: asNumber(item.score), grade: asString(item.grade) };
  };
  const hazard = isRecord(input.hazard) ? input.hazard : {};
  return {
    climate: simple(input.climate),
    soil: simple(input.soil),
    hazard: { safetyScore: asNumber(hazard.safetyScore), grade: asString(hazard.grade) },
    cultivation: simple(input.cultivation)
  };
};

export const normalizeRegionReport = (input: unknown, knownStatus?: AnalysisStatus | string): RegionReport => {
  if (!isRecord(input)) throw new Error('MALFORMED_REPORT');
  const rawStatus = asString(input.status) ?? asString(knownStatus);
  const status = rawStatus?.toUpperCase();
  if (status !== 'COMPLETED' && status !== 'PARTIAL') throw new Error('MALFORMED_REPORT_STATUS');

  const regionInput = isRecord(input.region) ? input.region : {};
  const region: RegionIdentity = {
    sidoCode: asString(regionInput.sidoCode) ?? '',
    sidoName: asString(regionInput.sidoName) ?? '',
    sigunguCode: asString(regionInput.sigunguCode) ?? '',
    sigunguName: asString(regionInput.sigunguName) ?? ''
  };
  const analysisId = asString(input.analysisId);
  if (!analysisId || !region.sidoCode || !region.sigunguCode) throw new Error('MALFORMED_REPORT_IDENTITY');

  const decision = isRecord(input.decision) ? input.decision : {};
  const confidenceInput = input.dataConfidence ?? decision.dataConfidence ?? input.confidence;
  return {
    analysisId,
    status: status as TerminalAnalysisStatus,
    analyzedAt: asString(input.analyzedAt),
    region,
    location: normalizeLocation(input.location),
    baseFitness: asNumber(input.baseFitness) ?? asNumber(decision.baseFitness),
    seasonReadiness: asNumber(input.seasonReadiness) ?? asNumber(decision.seasonReadiness),
    dataConfidence: normalizeConfidence(confidenceInput),
    regionScore: asNumber(input.regionScore),
    summary: asString(input.summary),
    components: normalizeComponents(input.components),
    environmentFeatures: asStringArray(input.environmentFeatures),
    recommendedCrops: asRecordArray(input.recommendedCrops ?? input.cropResults).map(normalizeCrop),
    topRisks: asRecordArray(input.topRisks ?? input.riskEvents).map(normalizeRisk),
    safeWorkWindows: asRecordArray(input.safeWorkWindows).map(normalizeSafeWindow),
    prioritizedActions: asRecordArray(input.prioritizedActions).map(normalizeAction),
    tips: asRecordArray(input.tips).map(normalizeAction),
    sources: asRecordArray(input.sources).map(normalizeSource),
    missingMetrics: asStringArray(input.missingMetrics)
  };
};

export const stateFromAnalysisStatus = (status: RegionAnalysisStatus, report?: RegionReport): AnalysisState => {
  const normalized = status.status.toUpperCase();
  if ((normalized === 'COMPLETED' || normalized === 'PARTIAL') && report) {
    return report.status === 'PARTIAL' ? { kind: 'PARTIAL', report } : { kind: 'COMPLETED', report };
  }
  if (normalized === 'FAILED') {
    return {
      kind: 'ERROR',
      message: status.errorMessage ?? '분석 결과를 만들지 못했습니다.',
      code: status.errorCode,
      retryable: status.retryable ?? true
    };
  }
  return {
    kind: 'POLLING',
    analysisId: status.analysisId,
    currentStep: status.currentStep,
    completedSteps: status.completedSteps ?? []
  };
};

export const canOpenReport = (state: AnalysisState): boolean => state.kind === 'COMPLETED' || state.kind === 'PARTIAL';
