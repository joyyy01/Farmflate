import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { EarthAnalysisIllustration } from '../illustrations/EarthAnalysisIllustration';
import type { AnalysisState } from '../../services/reportLifecycle';

interface AnalyzingViewProps {
  regionName: string;
  cropName?: string;
  analysisType?: 'region' | 'crop';
  state: AnalysisState;
  onRetry: () => void;
  onBack: () => void;
  onLogin: () => void;
}

export const AnalyzingView: React.FC<AnalyzingViewProps> = ({
  regionName,
  cropName = '작물',
  analysisType = 'region',
  state,
  onRetry,
  onBack,
  onLogin
}) => {
  const isCropMode = analysisType === 'crop';

  // 1. Regional Analysis Dedicated Steps & Terminology
  const regionSteps = [
    '지역 행정구역 및 법정동 정보 확인 중',
    '기상청 30일 기온 관측 및 단기예보 수집 중',
    '흙토람 시군구 농경지 토양 화학성 분석 중',
    '재배 적합 대표 작물군 및 추천 알고리즘 산출 중',
    '지역 통합 농사 환경 평가 점수 계산 중'
  ];

  // 2. Vegetable / Crop Specific Dedicated Steps & Terminology
  const cropSteps = [
    `${cropName} 최적 생육 온도 및 토양 요구조건 조회 중`,
    `${regionName} 기상청 단기예보·최근 기온 데이터 매칭 중`,
    `농경지 pH·유기물 및 ${cropName} 토양 적성 등급 평가 중`,
    `생육 주기별 기상 재해(고온·집중호우) 위험도 계산 중`,
    `${cropName} 맞춤형 밭 적합도 리포트 생성 중`
  ];

  const steps = isCropMode ? cropSteps : regionSteps;

  // Normalized Step Code Mapping
  const STEP_CODE_TO_INDEX: Record<string, number> = {
    'region': 0,
    'recent_weather': 1,
    'forecast': 1,
    'soil': 2,
    'crop': 3,
    'report': 4,
    '지역 정보 확인 중': 0,
    '기상청 데이터를 불러오는 중': 1,
    '흙토람 토양 정보를 분석하는 중': 2,
    '추천 작물을 계산하는 중': 3,
    '지역 농사 환경 점수를 산출하는 중': 4
  };

  const isWorking = state.kind === 'SUBMITTING' || state.kind === 'POLLING';
  const isUnauthorized = state.kind === 'UNAUTHORIZED';
  const errorMessage = state.kind === 'ERROR' || state.kind === 'UNAUTHORIZED' ? state.message : null;

  // Guaranteed smooth step-by-step progress timer
  const [simulatedStep, setSimulatedStep] = useState<number>(0);

  useEffect(() => {
    if (!isWorking) {
      setSimulatedStep(0);
      return;
    }
    const interval = setInterval(() => {
      setSimulatedStep(prev => (prev < steps.length - 1 ? prev + 1 : prev));
    }, 750);

    return () => clearInterval(interval);
  }, [isWorking, steps.length]);

  // Determine server target step index with full normalization
  let serverStepIndex = 0;
  if (state.kind === 'POLLING' && state.currentStep) {
    const norm = state.currentStep.trim().toLowerCase();
    if (STEP_CODE_TO_INDEX[norm] !== undefined) {
      serverStepIndex = STEP_CODE_TO_INDEX[norm];
    } else {
      const idx = steps.findIndex(s => s.toLowerCase() === norm);
      if (idx !== -1) serverStepIndex = idx;
    }
  }

  // Active step is the maximum of simulated step and server step
  const activeStep = isWorking ? Math.max(simulatedStep, serverStepIndex) : 0;

  // Completed steps calculation
  const pollingCompletedSteps = state.kind === 'POLLING' ? state.completedSteps : [];
  const completedIndices: number[] = isWorking 
    ? Array.from({ length: activeStep }, (_, i: number) => i) 
    : pollingCompletedSteps.map((s: string) => {
        const norm = s.trim().toLowerCase();
        return STEP_CODE_TO_INDEX[norm] ?? steps.findIndex(st => st.toLowerCase() === norm);
      }).filter((i: number) => i >= 0);

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
      padding: '40px 24px'
    }}>
      <div style={{
        width: '100%',
        maxWidth: 340,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
      }}>

        {/* 04. Earth Analysis Mascot Illustration */}
        <div style={{ marginBottom: 20 }}>
          <EarthAnalysisIllustration size={152} />
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
        <div style={{
          width: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          boxSizing: 'border-box',
          margin: '12px 0 20px 0'
        }}>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
            width: 'fit-content'
          }}>
            {steps.map((text, idx) => {
              const isCompleted = completedIndices.includes(idx) || (isWorking && idx < activeStep);
              const isCurrent = isWorking && idx === activeStep;

              return (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  {/* Status Icon */}
                  <div style={{ width: 22, height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    {isCompleted ? (
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="9.25" fill="#2FA35A" />
                        <path d="M8 12.3l2.6 2.6L16.3 9" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    ) : isCurrent ? (
                      <motion.svg
                        width="20"
                        height="20"
                        viewBox="0 0 24 24"
                        fill="none"
                        animate={{ rotate: 360 }}
                        transition={{ repeat: Infinity, duration: 1, ease: 'linear' }}
                      >
                        <circle cx="12" cy="12" r="9.25" stroke="#D7ECDD" strokeWidth="2.2" />
                        <path d="M21.25 12A9.25 9.25 0 0 0 12 2.75" stroke="#2FA35A" strokeWidth="2.2" strokeLinecap="round" />
                      </motion.svg>
                    ) : (
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="9.25" stroke="#D7DEDA" strokeWidth="1.6" />
                      </svg>
                    )}
                  </div>

                  {/* Step Label */}
                  <span style={{
                    fontSize: '0.88rem',
                    fontWeight: isCurrent ? 800 : isCompleted ? 700 : 500,
                    color: isCurrent ? '#154F36' : isCompleted ? '#202A24' : '#9CA3AF',
                    lineHeight: 1.4,
                    whiteSpace: 'nowrap'
                  }}>
                    {text}
                  </span>
                </div>
              );
            })}
          </div>
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
