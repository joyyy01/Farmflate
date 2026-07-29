import type { AnalysisState, FieldPreviewState } from './reportLifecycle';

const STEP_CODE_TO_INDEX: Record<string, number> = {
  REGION: 0,
  RECENT_WEATHER: 1,
  FORECAST: 1,
  SOIL: 2,
  CROP: 3,
  REPORT: 4
};

const KOREAN_STEP_TO_INDEX: Record<string, number> = {
  '지역 정보 확인 중': 0,
  '기상청 데이터를 불러오는 중': 1,
  '흙토람 토양 정보를 분석하는 중': 2,
  '추천 작물을 계산하는 중': 3,
  '지역 농사 환경 점수를 산출하는 중': 4
};

const resolveStepIndex = (code: string | null | undefined, steps: string[]): number => {
  if (!code) return 0;
  const upper = code.trim().toUpperCase();
  if (STEP_CODE_TO_INDEX[upper] !== undefined) return STEP_CODE_TO_INDEX[upper];
  const lower = code.trim().toLowerCase();
  const koreanEntry = Object.entries(KOREAN_STEP_TO_INDEX).find(([key]) => key.toLowerCase() === lower);
  if (koreanEntry) return koreanEntry[1];
  const index = steps.findIndex(step => step.toLowerCase() === lower);
  return index !== -1 ? index : 0;
};

const resolveCompletedStepIndex = (code: string | null | undefined, steps: string[]): number | null => {
  if (!code) return null;
  const upper = code.trim().toUpperCase();
  if (STEP_CODE_TO_INDEX[upper] !== undefined) return STEP_CODE_TO_INDEX[upper];
  const lower = code.trim().toLowerCase();
  const koreanEntry = Object.entries(KOREAN_STEP_TO_INDEX).find(([key]) => key.toLowerCase() === lower);
  if (koreanEntry) return koreanEntry[1];
  const index = steps.findIndex(step => step.toLowerCase() === lower);
  return index === -1 ? null : index;
};

export interface AnalysisStepDisplay {
  activeStepIndex: number | null;
  completedStepIndexes: number[];
}

/**
 * Converts lifecycle state into the single active row and completed rows shown
 * by the analysis screen. A terminal catch-up response can omit its current
 * code, so the next incomplete row remains active instead of restarting.
 */
export const deriveAnalysisStepDisplay = (
  state: AnalysisState | FieldPreviewState,
  steps: string[]
): AnalysisStepDisplay => {
  const stepCount = steps.length;
  if (stepCount === 0) return { activeStepIndex: null, completedStepIndexes: [] };

  const isWorking = state.kind === 'SUBMITTING' || state.kind === 'POLLING' || state.kind === 'COMPLETING';
  if (!isWorking) {
    const isComplete = state.kind === 'COMPLETED' || state.kind === 'PARTIAL';
    return {
      activeStepIndex: null,
      completedStepIndexes: isComplete ? Array.from({ length: stepCount }, (_, index) => index) : []
    };
  }

  let reportedActiveIndex = 0;
  const completed = new Set<number>();

  if (state.kind === 'POLLING') {
    reportedActiveIndex = state.currentStepCode
      ? resolveStepIndex(state.currentStepCode, steps)
      : state.currentStep
        ? resolveStepIndex(state.currentStep, steps)
        : 0;

    const reportedCompleted = state.completedStepCodes.length > 0
      ? state.completedStepCodes.map(code => resolveCompletedStepIndex(code, steps))
      : state.completedSteps.map(step => resolveCompletedStepIndex(step, steps));
    reportedCompleted.forEach(index => {
      if (index !== null && index >= 0 && index < stepCount) completed.add(index);
    });
  } else if (state.kind === 'COMPLETING') {
    reportedActiveIndex = state.completedStepIndex;
  } else if (state.kind === 'SUBMITTING') {
    reportedActiveIndex = 'step' in state ? state.step : 0;
  }

  reportedActiveIndex = Math.min(Math.max(reportedActiveIndex, 0), stepCount - 1);
  for (let index = 0; index < reportedActiveIndex; index += 1) completed.add(index);

  let activeStepIndex = reportedActiveIndex;
  if (completed.has(activeStepIndex)) {
    const nextIncomplete = Array.from({ length: stepCount }, (_, index) => index)
      .find(index => index > reportedActiveIndex && !completed.has(index));
    activeStepIndex = nextIncomplete ?? Math.max(0, stepCount - 1);
    completed.delete(activeStepIndex);
  }

  return {
    activeStepIndex,
    completedStepIndexes: Array.from(completed).sort((left, right) => left - right)
  };
};
