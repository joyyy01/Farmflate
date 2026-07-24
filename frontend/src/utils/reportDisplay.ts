const exactStatusLabels: Record<string, string> = {
  GOOD: '양호',
  EXCELLENT: '매우 양호',
  MODERATE: '보통',
  FAIR: '주의',
  CAUTION: '주의',
  WARNING: '위험',
  POOR: '위험',
  RISK: '주의',
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
  LIVE: '실시간 공공데이터',
  REPLAY: '저장된 분석 데이터',
  LETTUCE_HEAT_HUMIDITY: '상추 고온·다습 주의',
  HEAT: '고온 위험',
  HEAVY_RAIN: '집중호우 위험'
};

const reportTextReplacements: Array<[RegExp, string]> = [
  [/LETTUCE_HEAT_HUMIDITY/g, '상추 고온·다습 주의'],
  [/\bHEAVY_RAIN\b/g, '집중호우 위험'],
  [/\bHEAT\b/g, '고온 위험'],
  [/\bDROUGHT\b/g, '가뭄 위험'],
  [/\bFROST\b/g, '서리 위험'],
  [/lettuce heat exposure\s*→\s*high humidity\s*→\s*combined heat-humidity stress/gi, '고온과 높은 습도가 겹쳐 작물 스트레스가 커질 수 있습니다.'],
  [/high relative humidity\s*→\s*reduced evaporation and\s*disease-pressure exposure/gi, '상대습도가 높아 증발량이 줄고 병해 발생 가능성이 있습니다.'],
  [/\bMODERATE\b/g, '보통'],
  [/\bCAUTION\b/g, '주의'],
  [/\bWARNING\b/g, '위험'],
  [/\bRISK\b/g, '주의'],
  [/\bGOOD\b/g, '양호'],
  [/\bEXCELLENT\b/g, '매우 양호'],
  [/\bHIGH\b/g, '높음'],
  [/\bLIVE\b/g, '실시간 공공데이터']
];

/** UI-only formatter. Domain codes remain untouched in API state. */
export const formatReportLabel = (value?: string | null, fallback = '자료 부족'): string => {
  const normalized = value?.trim();
  if (!normalized) return fallback;
  return exactStatusLabels[normalized.toUpperCase()] ?? formatReportText(normalized);
};

export const formatReportText = (value?: string | null, fallback = '자료 부족'): string => {
  const normalized = value?.trim();
  if (!normalized) return fallback;
  return reportTextReplacements.reduce((text, [pattern, replacement]) => text.replace(pattern, replacement), normalized);
};

export const formatReportDate = (value?: string | null, fallback = '날짜 정보 없음'): string => {
  const normalized = value?.trim();
  if (!normalized) return fallback;
  const compact = normalized.match(/^(\d{4})(\d{2})(\d{2})$/);
  if (compact) return `${compact[1]}년 ${Number(compact[2])}월 ${Number(compact[3])}일`;
  const iso = normalized.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (iso) return `${iso[1]}년 ${Number(iso[2])}월 ${Number(iso[3])}일`;
  return formatReportText(normalized, fallback);
};
