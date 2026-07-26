import { describe, expect, it } from 'vitest';
import { buildFieldVisibleData, buildRegionVisibleData } from '../services/visibleDataContext';
import type { FieldDashboardResponse, RegionReport } from '../types/report';

describe('visible screen data context', () => {
  it('maps the field dashboard score, alert, task, and reasoning without display values', () => {
    const dashboard = {
      field: { id: 'field-1', fieldName: '텃밭', cropCode: 'LETTUCE', cropName: '상추', regionName: '수원시', cultivationStartDate: null, cultivationDay: 18, stage: '생장기' },
      report: { id: 'report-1', reportDate: '2026-07-26', generatedAt: '2026-07-26T06:00:00+09:00', generationReason: 'DAILY_0630', status: 'CAUTION', headline: '오후 고온 주의', headlineDescription: '고온 시간대에 잎이 처질 수 있습니다.', historical: false, taskCountBeforeAcknowledgement: 1, statusScore: 62, statusScoreZone: '주의' },
      weather: { status: 'AVAILABLE', currentTemperature: 27, minTemperature: 21, maxTemperature: 31, precipitationProbability: 0, rainfallMm: 0, humidity: 58, windSpeed: 1.2, condition: '맑음' },
      soil: { available: true, ph: 6.1, ec: 0.8 },
      tasks: [{ key: 'CHECK_SHADE', title: '차광과 통풍 확인', description: '강한 햇빛을 줄이고 바람길을 확인하세요.', badge: 'MORNING_RECOMMENDED', acknowledged: false }],
      alerts: [{ key: 'HIGH_TEMPERATURE', severity: 'MEDIUM', title: '오후 고온 주의', description: '고온 시간대에 잎이 처질 수 있습니다.' }],
      reasoning: { summary: '고온에 대비해 차광을 확인하세요.', points: ['최고 기온이 생육 적온보다 높습니다.'] },
      todayLogs: [],
      history: [],
    } as FieldDashboardResponse;

    const refs = buildFieldVisibleData(dashboard);

    expect(refs).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'field.score', section: 'field' }),
      expect.objectContaining({ key: 'field.alert.1', section: 'field' }),
      expect.objectContaining({ key: 'field.task.1', section: 'field' }),
      expect.objectContaining({ key: 'field.reasoning.1', section: 'field' }),
      expect.objectContaining({ key: 'field.weather.maxTemperature', section: 'field' }),
    ]));
    expect(refs.every(ref => ref.displayValue === undefined)).toBe(true);
  });

  it('maps the visible region score, components, risks, and ranked crops', () => {
    const report = {
      analysisId: 'analysis-1', status: 'COMPLETED', region: { sidoCode: '41', sidoName: '경기도', sigunguCode: '41110', sigunguName: '수원시' },
      baseFitness: 74, seasonReadiness: 68, dataConfidence: { score: 88, level: 'HIGH' }, regionScore: 71,
      components: { climate: { score: 78, grade: '적정' }, soil: { score: 72, grade: '적정' }, hazard: { safetyScore: 62, grade: '주의' }, cultivation: { score: 70, grade: '적정' } },
      environmentFeatures: [],
      recommendedCrops: [{ cropName: '상추', score: 82, rank: 1, positiveReasons: [] }, { cropName: '오이', score: 76, rank: 2, positiveReasons: [] }],
      cropResults: [],
      topRisks: [{ title: '오후 고온 주의', riskCode: 'HIGH_TEMPERATURE', affectedCrops: [], actions: [], causalChain: [], evidenceRefs: [] }],
      prioritizedActions: [], tips: [], sources: [], missingMetrics: [],
    } as RegionReport;

    expect(buildRegionVisibleData(report)).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'region.score', section: 'summary' }),
      expect.objectContaining({ key: 'component.soil.score', section: 'soil' }),
      expect.objectContaining({ key: 'risk.1', section: 'hazard' }),
      expect.objectContaining({ key: 'crop.1', section: 'crop' }),
      expect.objectContaining({ key: 'crop.2', section: 'crop' }),
    ]));
  });
});
