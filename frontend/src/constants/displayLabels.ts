export const STAGE_LABELS: Record<string, string> = {
  BEFORE: '심기 전',
  PRE_PLANTING: '심기 전',
  GROWING: '생장기',
  EARLY_GROWTH: '생장 초기',
  HARVEST: '수확기',
  UNSPECIFIED: '단계 정보 없음',
};

export const FIELD_STATUS_LABELS = {
  STABLE: '안정',
  CAUTION: '주의',
  NEEDS_CHECK: '확인 필요',
} as const;

export const TASK_BADGE_LABELS = {
  MORNING_RECOMMENDED: '오전 권장',
  CHECK_ANYTIME: '수시 확인',
} as const;

export const LOG_CATEGORY_LABELS = {
  WATERING: '물주기',
  FERTILIZING: '비료',
  LEAF_CHECK: '잎 상태 확인',
  PEST_CONTROL: '병해충 방제',
  OTHER: '기타',
} as const;

export const GRADE_LABELS: Record<string, string> = {
  EXCELLENT: '최적',
  VERY_GOOD: '양호',
  GOOD: '양호',
  MODERATE: '보통',
  CAUTION: '주의',
  HIGH: '위험',
  WARNING: '경고',
  UNAVAILABLE: '자료 부족',
};

export const SUITABILITY_STATUS_LABELS: Record<string, string> = {
  GOOD: '양호',
  CAUTION: '주의',
  RISK: '위험',
  UNAVAILABLE: '자료 부족',
  INPUT_RECORDED: '입력 기준',
};

export const displayStage = (stage?: string | null): string => {
  if (!stage) return STAGE_LABELS.UNSPECIFIED;
  return STAGE_LABELS[stage.trim().toUpperCase()] ?? '단계 정보 없음';
};

export const displayGrade = (grade?: string | null): string => {
  if (!grade) return '자료 부족';
  return GRADE_LABELS[grade.trim().toUpperCase()] ?? '자료 부족';
};

export const displaySuitabilityStatus = (status?: string | null): string => {
  if (!status) return '자료 부족';
  return SUITABILITY_STATUS_LABELS[status.trim().toUpperCase()] ?? '자료 부족';
};

export const displayFieldDailyStatus = (status?: string | null): string => {
  if (!status) return FIELD_STATUS_LABELS.NEEDS_CHECK;
  const key = status.trim().toUpperCase() as keyof typeof FIELD_STATUS_LABELS;
  return FIELD_STATUS_LABELS[key] ?? FIELD_STATUS_LABELS.NEEDS_CHECK;
};
