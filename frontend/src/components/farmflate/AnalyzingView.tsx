import React from 'react';
import { motion } from 'framer-motion';
import { EarthAnalysisIllustration } from '../illustrations/EarthAnalysisIllustration';
import type { AnalysisState } from '../../services/reportLifecycle';

interface AnalyzingViewProps {
  regionName: string;
  state: AnalysisState;
  onRetry: () => void;
  onBack: () => void;
  onLogin: () => void;
}

export const AnalyzingView: React.FC<AnalyzingViewProps> = ({
  regionName,
  state,
  onRetry,
  onBack,
  onLogin
}) => {
  const regionSteps = [
    '지역 정보 확인 중',
    '기상청 데이터를 불러오는 중',
    '흙토람 토양 정보를 분석하는 중',
    '추천 작물을 계산하는 중',
    '지역 농사 환경 점수를 산출하는 중'
  ];

  const steps = regionSteps;
  const completedSteps = state.kind === 'POLLING' ? state.completedSteps : [];
  const activeStep = state.kind === 'POLLING' && state.currentStep ? Math.max(0, steps.findIndex(step => step === state.currentStep)) : state.kind === 'SUBMITTING' ? 0 : 0;
  const isWorking = state.kind === 'SUBMITTING' || state.kind === 'POLLING';
  const isUnauthorized = state.kind === 'UNAUTHORIZED';
  const errorMessage = state.kind === 'ERROR' || state.kind === 'UNAUTHORIZED' ? state.message : null;
  const title = isWorking ? '지역 환경 분석 중...' : '분석을 완료하지 못했어요';
  const subtitle = isWorking ? `${regionName}의 기후와 토양 데이터를\n서버에서 확인하고 있습니다` : errorMessage ?? '선택한 지역은 그대로 유지되어 있습니다.';

  return (
    <div className="full-screen-view" style={{
      backgroundColor: '#FFFFFF',
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

        {/* Animated Steps */}
        <div style={{ width: '100%', maxWidth: 280, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {steps.map((text, idx) => {
            const isCompleted = completedSteps.includes(text) || (isWorking && idx < activeStep);
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
          lineHeight: 1.55
        }}>
          {isWorking
            ? '공공데이터를 기반으로 분석 중입니다.\n완료 상태가 확인될 때까지 기다려 주세요.'
            : '분석 실패 시에는 리포트로 이동하지 않습니다.\n다시 시도하거나 지역을 변경할 수 있어요.'
          }
        </p>

        {!isWorking && (
          <div role="alert" aria-live="assertive" style={{ width: '100%', display: 'flex', gap: 8, marginTop: 8 }}>
            <button type="button" onClick={onBack} style={{ flex: 1, height: 44, borderRadius: 12, border: '1px solid #D1DFD7', background: '#FFFFFF', color: '#2FA86A', fontWeight: 800 }}>지역 변경</button>
            {isUnauthorized ? <button type="button" onClick={onLogin} className="btn-farm-primary" style={{ flex: 1, height: 44, borderRadius: 12 }}>로그인</button> : <button type="button" onClick={onRetry} className="btn-farm-primary" style={{ flex: 1, height: 44, borderRadius: 12 }}>다시 시도</button>}
          </div>
        )}

      </div>
    </div>
  );
};
