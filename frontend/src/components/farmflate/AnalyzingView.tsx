import React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { CheckCircle2, Loader2 } from 'lucide-react';
import type { AnalysisState, FieldPreviewState } from '../../services/reportLifecycle';

interface AnalyzingViewProps {
  regionName: string;
  cropName?: string;
  analysisType?: 'region' | 'crop';
  state: AnalysisState | FieldPreviewState;
  onRetry: () => void;
  onBack: () => void;
  onLogin: () => void;
  /** Optional label (e.g. field/crop name) shown as a small badge in crop mode illustrations. */
  fieldLabel?: string;
}

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
  const koreanEntry = Object.entries(KOREAN_STEP_TO_INDEX).find(([k]) => k.toLowerCase() === lower);
  if (koreanEntry) return koreanEntry[1];
  const idx = steps.findIndex(s => s.toLowerCase() === lower);
  return idx !== -1 ? idx : 0;
};

const resolveCompletedStepIndex = (code: string | null | undefined, steps: string[]): number | null => {
  if (!code) return null;
  const upper = code.trim().toUpperCase();
  if (STEP_CODE_TO_INDEX[upper] !== undefined) return STEP_CODE_TO_INDEX[upper];
  const lower = code.trim().toLowerCase();
  const koreanEntry = Object.entries(KOREAN_STEP_TO_INDEX).find(([key]) => key.toLowerCase() === lower);
  if (koreanEntry) return koreanEntry[1];
  const textIndex = steps.findIndex(step => step.toLowerCase() === lower);
  return textIndex === -1 ? null : textIndex;
};

export interface AnalysisStepDisplay {
  activeStepIndex: number | null;
  completedStepIndexes: number[];
}

/**
 * Keeps the loader a pure projection of lifecycle state. In particular, a
 * terminal catch-up response may contain completed codes but no current code;
 * the next incomplete row must become the active spinner instead of rewinding
 * the indicator to the first row.
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

export const AnalyzingView: React.FC<AnalyzingViewProps> = ({
  regionName,
  cropName = '작물',
  analysisType = 'region',
  state,
  onRetry,
  onBack,
  onLogin,
  fieldLabel
}) => {
  const isCropMode = analysisType === 'crop';

  const regionSteps = [
    '지역 행정구역 및 법정동 정보 확인 중',
    '기상청 30일 기온 관측 및 단기예보 수집 중',
    '흙토람 시군구 농경지 토양 화학성 분석 중',
    '재배 적합 대표 작물군 및 추천 알고리즘 산출 중',
    '지역 통합 농사 환경 평가 점수 계산 중'
  ];

  const cropSteps = [
    `${cropName} 최적 생육 온도 및 토양 조건 확인 중`,
    `지역 기상청 단기예보 및 최근 기온 데이터 매칭 중`,
    `농경지 pH·유기물 및 ${cropName} 토양 적성 평가 중`,
    `생육 주기별 기상 재해(고온·강우) 위험도 계산 중`,
    `${cropName} 맞춤형 밭 적합도 리포트 생성 중`
  ];

  const steps = isCropMode ? cropSteps : regionSteps;

  // Normalize state into common display values
  const isWorking = state.kind === 'SUBMITTING' || state.kind === 'POLLING' || state.kind === 'COMPLETING';
  const isUnauthorized = state.kind === 'UNAUTHORIZED';
  const errorMessage = state.kind === 'ERROR' || state.kind === 'UNAUTHORIZED' ? state.message : null;
  const { activeStepIndex, completedStepIndexes } = deriveAnalysisStepDisplay(state, steps);
  const completedSteps = new Set(completedStepIndexes);

  const title = isWorking
    ? (isCropMode ? `${cropName} 생육 적합도 분석 중...` : '지역 종합 환경 분석 중...')
    : '분석을 완료하지 못했어요';

  const subtitle = isWorking
    ? (isCropMode
        ? `${regionName} 환경과 ${cropName}의 생육 파라미터를\n정밀 교차 검증하고 있습니다`
        : `${regionName}의 토양, 기후, 기상 데이터를\n통합 분석하고 있습니다`)
    : errorMessage ?? '선택한 정보는 안전하게 유지되고 있습니다.';

  const footerText = isWorking
    ? (isCropMode
        ? `${cropName} 품목 맞춤형 공공 농업 생육 데이터를 검증 중입니다.\n완료 상태가 확인될 때까지 잠시만 기다려 주세요.`
        : '공공데이터(기상청·농촌진흥청) 기반으로 종합 분석 중입니다.\n완료될 때까지 잠시만 기다려 주세요.')
    : (isCropMode
        ? `${cropName} 적합도 검증에 실패했습니다.\n다시 시도하거나 조건을 변경할 수 있어요.`
        : '지역 분석에 실패했습니다.\n다시 시도하거나 다른 지역을 선택할 수 있어요.');

  const backButtonLabel = isCropMode ? '밭 정보 다시 입력' : '지역 다시 선택';

  return (
    <div className="full-screen-view" style={{
      backgroundColor: '#FFFFFF',
      minHeight: '100dvh',
      height: '100dvh',
      boxSizing: 'border-box',
      justifyContent: 'center',
      alignItems: 'center',
      padding: '40px 20px'
    }}>
      <div style={{
        width: '100%',
        maxWidth: 340,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
      }}>

        {/* Field/Crop Badge (Crop Mode Only) */}
        {isCropMode && fieldLabel && (
          <div style={{
            backgroundColor: '#E9F7EC', color: '#2FA86A',
            fontSize: '0.78rem', fontWeight: 800,
            padding: '6px 14px', borderRadius: 14,
            marginBottom: 16
          }}>
            🌱 {fieldLabel}
          </div>
        )}

        {/* 04. Analysis Mascot Illustration */}
        <div style={{ marginBottom: 20 }}>
          {isCropMode ? (
            <img
              src="/assets/crop-analyzing-mascot.png"
              alt="농작물 적합도 분석 마스코트"
              style={{ width: '100%', maxWidth: 260, height: 'auto', objectFit: 'contain' }}
            />
          ) : (
            <img
              src="/assets/region-analyzing-globe.png"
              alt="지역 환경 분석 마스코트"
              style={{ width: '100%', maxWidth: 220, height: 'auto', objectFit: 'contain' }}
            />
          )}
        </div>

        {/* Title */}
        <h2 style={{
          fontSize: '1.45rem',
          fontWeight: 900,
          color: '#154F36',
          textAlign: 'center',
          marginBottom: 8,
          lineHeight: 1.3
        }}>
          {title}
        </h2>

        {/* Subtitle */}
        <p style={{
          fontSize: '0.86rem',
          color: '#6F7772',
          fontWeight: 500,
          textAlign: 'center',
          marginBottom: 28,
          lineHeight: 1.6,
          whiteSpace: 'pre-line'
        }}>
          {subtitle}
        </p>

        {/* Animated Steps List */}
        <div className="analysis-step-list" aria-live="polite" aria-label="분석 진행 상태">
          {steps.map((text, idx) => {
            const isCompleted = completedSteps.has(idx);
            const isCurrent = activeStepIndex === idx;

            return (
              <motion.div
                layout
                key={idx}
                className={`analysis-step${isCurrent ? ' analysis-step--active' : ''}${isCompleted ? ' analysis-step--complete' : ''}`}
              >
                {/* Status icon */}
                <span className="analysis-step__adornment">
                  <AnimatePresence mode="wait" initial={false}>
                    {isCompleted ? (
                      <motion.span key="complete" initial={{ opacity: 0, scale: 0.65 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.65 }} transition={{ duration: 0.18 }}>
                        <CheckCircle2 className="analysis-step__complete-icon" aria-label="완료" />
                      </motion.span>
                    ) : isCurrent ? (
                      <motion.span key="active" initial={{ opacity: 0, y: -5 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 5 }} transition={{ duration: 0.16 }}>
                        <Loader2 className="analysis-step__spinner" aria-label="분석 중" />
                      </motion.span>
                    ) : (
                      <motion.span key="pending" className="analysis-step__pending" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} />
                    )}
                  </AnimatePresence>
                </span>

                {/* Step label */}
                <span className="analysis-step__label">
                  {text}
                </span>
              </motion.div>
            );
          })}
        </div>

        {/* Footer info text */}
        <p style={{
          fontSize: '0.78rem',
          color: '#94A3B8',
          fontWeight: 500,
          textAlign: 'center',
          marginTop: 28,
          lineHeight: 1.55,
          whiteSpace: 'pre-line'
        }}>
          {footerText}
        </p>

        {!isWorking && (
          <div role="alert" aria-live="assertive" style={{ width: '100%', display: 'flex', gap: 8, marginTop: 8 }}>
            <button type="button" onClick={onBack} style={{ flex: 1, height: 44, borderRadius: 12, border: '1px solid #D1DFD7', background: '#FFFFFF', color: '#2FA86A', fontWeight: 800 }}>{backButtonLabel}</button>
            {isUnauthorized ? <button type="button" onClick={onLogin} className="btn-farm-primary" style={{ flex: 1, height: 44, borderRadius: 12 }}>로그인</button> : <button type="button" onClick={onRetry} className="btn-farm-primary" style={{ flex: 1, height: 44, borderRadius: 12 }}>다시 시도</button>}
          </div>
        )}

      </div>
    </div>
  );
};
