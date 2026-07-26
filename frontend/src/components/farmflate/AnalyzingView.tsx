import React from 'react';
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

  // Server-driven step (from POLLING or COMPLETING)
  let serverStep = 0;
  let serverCompleted: number[] = [];

  if (state.kind === 'POLLING') {
    const codeIndex = resolveStepIndex(state.currentStepCode, steps);
    const labelIndex = resolveStepIndex(state.currentStep, steps);
    serverStep = state.currentStepCode ? codeIndex : labelIndex;

    const codes = state.completedStepCodes ?? [];
    if (codes.length > 0) {
      serverCompleted = codes
        .map(c => STEP_CODE_TO_INDEX[c.toUpperCase()])
        .filter((i): i is number => i !== undefined);
    } else {
      serverCompleted = (state.completedSteps ?? [])
        .map(s => resolveStepIndex(s, steps))
        .filter(i => i >= 0);
    }
    for (let i = 0; i < serverStep; i++) {
      if (!serverCompleted.includes(i)) serverCompleted.push(i);
    }
  } else if (state.kind === 'COMPLETING') {
    serverStep = state.completedStepIndex;
    serverCompleted = Array.from({ length: state.completedStepIndex }, (_, i) => i);
  } else if (state.kind === 'SUBMITTING') {
    const step = 'step' in state ? state.step : 0;
    serverStep = step;
    serverCompleted = Array.from({ length: step }, (_, i) => i);
  }

  const activeStep = serverStep;
  const completedIndices = serverCompleted;

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
        <div style={{ width: '100%', maxWidth: 280, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {steps.map((text, idx) => {
            const isCompleted = completedIndices.includes(idx) || (isWorking && idx < activeStep);
            const isCurrent = isWorking && idx === activeStep;

            return (
              <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                {/* Status icon */}
                <div style={{ width: 22, height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  {isCompleted ? (
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                      <circle cx="12" cy="12" r="9.25" fill="#2FA35A" />
                      <path d="M8 12.3l2.6 2.6L16.3 9" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  ) : isCurrent ? (
                    <div
                      key={`spin-${idx}`}
                      className="spinner-rotate"
                      style={{ width: 20, height: 20, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    >
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="9.25" stroke="#D7ECDD" strokeWidth="2.2" />
                        <path d="M21.25 12A9.25 9.25 0 0 0 12 2.75" stroke="#2FA35A" strokeWidth="2.2" strokeLinecap="round" />
                      </svg>
                    </div>
                  ) : (
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                      <circle cx="12" cy="12" r="9.25" stroke="#D7DEDA" strokeWidth="1.6" />
                    </svg>
                  )}
                </div>

                {/* Step label */}
                <span style={{
                  fontSize: '0.88rem',
                  fontWeight: isCurrent ? 800 : isCompleted ? 700 : 500,
                  color: isCurrent ? '#154F36' : isCompleted ? '#202A24' : '#9CA3AF',
                  lineHeight: 1.4
                }}>
                  {text}
                </span>
              </div>
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
