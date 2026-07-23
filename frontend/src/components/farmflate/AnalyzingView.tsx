import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { EarthAnalysisIllustration } from '../illustrations/EarthAnalysisIllustration';

interface AnalyzingViewProps {
  regionName: string;
  onComplete: () => void;
  mode?: 'region' | 'crop';
}

export const AnalyzingView: React.FC<AnalyzingViewProps> = ({
  regionName: _regionName,
  onComplete,
  mode = 'region'
}) => {
  const [activeStep, setActiveStep] = useState(0);

  const regionSteps = [
    '지역 정보 확인 중',
    '기상청 데이터를 불러오는 중',
    '흙토람 토양 정보를 분석하는 중',
    '추천 작물을 계산하는 중',
    '지역 농사 환경 점수를 산출하는 중'
  ];

  const cropSteps = [
    '선택한 작물을 확인하는 중',
    '재배 방식을 적용하는 중',
    '기후 적합도를 계산하는 중',
    '토양 적합도를 계산하는 중',
    '핵심 위험 요소를 분석하는 중',
    '시작 전 준비사항을 생성하는 중'
  ];

  const steps = mode === 'region' ? regionSteps : cropSteps;
  const title = mode === 'region' ? '지역 환경 분석 중...' : '농작물 적합도 분석 중...';
  const subtitle = mode === 'region'
    ? `선택한 지역의 기후와 토양 데이터를\n분석하고 있습니다`
    : `입력한 밭 정보를 바탕으로 재배 적합도를 계산하고 있습니다`;

  useEffect(() => {
    const timer = setInterval(() => {
      setActiveStep(prev => {
        if (prev < steps.length - 1) {
          return prev + 1;
        } else {
          clearInterval(timer);
          setTimeout(() => onComplete(), 800);
          return prev;
        }
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [onComplete, steps.length]);

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
            const isCompleted = idx < activeStep;
            const isCurrent = idx === activeStep;

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
          {mode === 'region'
            ? '공공데이터를 기반으로 분석 중입니다.\n잠시만 기다려 주세요.'
            : '현재 지역 환경과 작물 기준을 비교하여\n재배 적합도를 분석하고 있습니다.'
          }
        </p>

      </div>
    </div>
  );
};
