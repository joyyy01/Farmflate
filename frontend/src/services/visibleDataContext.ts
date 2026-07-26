import type { VisibleDataRef, VisibleDataSection } from '../types/chat';
import type { FieldDashboardResponse, RegionReport } from '../types/report';

const MAX_VISIBLE_DATA_REFS = 12;

function addRef(
  refs: VisibleDataRef[],
  key: string,
  label: string,
  section: VisibleDataSection,
  present: boolean,
): void {
  if (!present || refs.length >= MAX_VISIBLE_DATA_REFS || refs.some(ref => ref.key === key)) return;
  refs.push({ key, label, section });
}

export function buildRegionVisibleData(report: RegionReport | null | undefined): VisibleDataRef[] {
  if (!report) return [];
  const refs: VisibleDataRef[] = [];
  addRef(refs, 'region.score', '종합 적합도 점수', 'summary', report.regionScore != null);
  addRef(refs, 'region.summary', '지역 분석 요약', 'summary', Boolean(report.summary));
  addRef(refs, 'component.climate.score', '기후 적합도', 'climate', report.components?.climate?.score != null);
  addRef(refs, 'component.soil.score', '토양 적합도', 'soil', report.components?.soil?.score != null);
  addRef(refs, 'component.hazard.safetyScore', '위험 안전 점수', 'hazard', report.components?.hazard?.safetyScore != null);
  addRef(refs, 'component.cultivation.score', '재배 적합도', 'crop', report.components?.cultivation?.score != null);
  report.topRisks.slice(0, 3).forEach((risk, index) => {
    addRef(refs, `risk.${index + 1}`, risk.title || `주의·위험 ${index + 1}`, 'hazard', Boolean(risk.title));
  });
  report.recommendedCrops.slice(0, 5).forEach((crop, index) => {
    addRef(refs, `crop.${index + 1}`, crop.cropName || `추천 작물 ${index + 1}`, 'crop', Boolean(crop.cropName));
  });
  return refs;
}

export function buildFieldVisibleData(dashboard: FieldDashboardResponse | null | undefined): VisibleDataRef[] {
  if (!dashboard) return [];
  const refs: VisibleDataRef[] = [];
  addRef(refs, 'field.score', '오늘 종합 상태 점수', 'field', dashboard.report.statusScore != null);
  addRef(refs, 'field.headline', '오늘 안내', 'field', Boolean(dashboard.report.headline));
  addRef(refs, 'field.weather.minTemperature', '오늘 최저 기온', 'field', dashboard.weather.minTemperature != null);
  addRef(refs, 'field.weather.maxTemperature', '오늘 최고 기온', 'field', dashboard.weather.maxTemperature != null);
  addRef(refs, 'field.weather.humidity', '오늘 습도', 'field', dashboard.weather.humidity != null);
  addRef(refs, 'field.weather.rainfall', '오늘 강수량', 'field', dashboard.weather.rainfallMm != null);
  addRef(refs, 'field.soil.ph', '토양 pH', 'field', dashboard.soil?.available === true && dashboard.soil.ph != null);
  addRef(refs, 'field.soil.ec', '토양 EC', 'field', dashboard.soil?.available === true && dashboard.soil.ec != null);
  const alert = dashboard.alerts[0];
  addRef(refs, 'field.alert.1', alert?.title || '오늘의 주의·위험', 'field', Boolean(alert));
  const task = dashboard.tasks[0];
  addRef(refs, 'field.task.1', task?.title || '오늘 할 일', 'field', Boolean(task));
  addRef(refs, 'field.reasoning.1', '왜 이렇게 안내했나요?', 'field', dashboard.reasoning.points.length > 0);
  return refs;
}

export function visibleDataSignature(visibleData: VisibleDataRef[] | undefined): string {
  return (visibleData ?? []).map(ref => `${ref.key}:${ref.label}:${ref.section}`).join('|');
}
