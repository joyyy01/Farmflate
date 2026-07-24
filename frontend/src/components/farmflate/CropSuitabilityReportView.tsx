import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, AlertTriangle } from 'lucide-react';
import type { RegionReport } from '../../services/api';

interface CropSuitabilityReportViewProps {
  fieldName?: string;
  cropName?: string;
  score?: number | null;
  report?: RegionReport | null;
  onBack: () => void;
  onRegisterCrop: () => void;
}

export const CropSuitabilityReportView: React.FC<CropSuitabilityReportViewProps> = ({
  fieldName,
  cropName,
  score,
  report,
  onBack,
  onRegisterCrop
}) => {
  const crop = report?.recommendedCrops.find(item => item.cropName === cropName);
  const reportScore = score ?? crop?.score ?? null;
  const numericScore = reportScore ?? 0;
  const risks = (report?.topRisks ?? []).slice(0, 3);
  const prePlantActions = (report?.prioritizedActions ?? []).filter(action => Boolean(action.stage && /(PRE|전|준비)/i.test(action.stage)));
  const growingActions = (report?.prioritizedActions ?? []).filter(action => Boolean(action.stage && /(CULT|재배|생육)/i.test(action.stage)));
  const componentValue = (component?: { score?: number | null; safetyScore?: number | null; grade?: string | null } | null) => {
    if (component?.grade) return component.grade;
    const value = component?.score ?? component?.safetyScore;
    return typeof value === 'number' ? `${value}점` : '자료 부족';
  };
  const environmentCards = [
    { icon: '🌦️', label: '기후 적합도', value: componentValue(report?.components?.climate) },
    { icon: '🌱', label: '토양 적합도', value: componentValue(report?.components?.soil) },
    { icon: '🏡', label: '재배 환경', value: componentValue(report?.components?.cultivation) },
    { icon: '⚠️', label: '위험도', value: componentValue(report?.components?.hazard), caution: true }
  ];
  const explanation = crop?.positiveReasons?.[0]
    ?? crop?.cautionReason
    ?? report?.environmentFeatures?.[0]
    ?? '분석 근거가 아직 제공되지 않았습니다.';
  const analysisDate = report?.analyzedAt
    ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(report.analyzedAt))
    : '제공되지 않음';

  // SVG Ring Gauge Calculations
  const radius = 64;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (circumference * numericScore) / 100;

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#F4FBF5', display: 'flex', flexDirection: 'column', height: '100%', position: 'relative', overflow: 'hidden' }}>
      
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '0 20px 100px 20px' }}>
        
        {/* Header */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '32px 1fr 32px',
          alignItems: 'center',
          height: 60,
          borderBottom: '1px solid #E2F2E5',
          marginBottom: 20
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#191F28', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.15rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center' }}>
            농작물 적합도 리포트
          </h1>
          <div />
        </div>

        {/* Field & Crop Subtitle */}
        <div style={{ fontSize: '0.94rem', fontWeight: 850, color: '#154F36', marginBottom: 20 }}>
          {fieldName || '밭 이름 정보 없음'} · {cropName || '작물 정보 없음'}
        </div>

        {/* Circular Ring Gauge & Score */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: 24 }}>
          <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 600, marginBottom: 12 }}>
            농작물 적합도 점수
          </span>

          <div style={{ position: 'relative', width: 160, height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 14 }}>
            <svg width="160" height="160" viewBox="0 0 160 160" style={{ transform: 'rotate(-90deg)' }}>
              <circle
                cx="80"
                cy="80"
                r={radius}
                stroke="#D8F3DF"
                strokeWidth="12"
                fill="transparent"
              />
              <circle
                cx="80"
                cy="80"
                r={radius}
                stroke="url(#cropGradient)"
                strokeWidth="12"
                fill="transparent"
                strokeDasharray={circumference}
                strokeDashoffset={strokeDashoffset}
                strokeLinecap="round"
                style={{ transition: 'stroke-dashoffset 0.9s cubic-bezier(0.16, 1, 0.3, 1)' }}
              />
              <defs>
                <linearGradient id="cropGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#86EFAC" />
                  <stop offset="100%" stopColor="#2FA86A" />
                </linearGradient>
              </defs>
            </svg>

            {/* Center Score Typography */}
            <div style={{ position: 'absolute', display: 'flex', alignItems: 'baseline', justifyContent: 'center' }}>
              <span style={{ fontSize: '3.4rem', fontWeight: 900, color: '#154F36', letterSpacing: '-0.05em', lineHeight: 1 }}>
                {reportScore === null ? '자료 부족' : reportScore}
              </span>
            </div>
          </div>

          <h2 style={{ fontSize: '1.08rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center' }}>
            {crop?.positiveReasons[0] ?? crop?.cautionReason ?? '작물별 판단 자료가 제공되지 않았습니다.'}
          </h2>
        </div>

        {/* 4 Environment Status Cards (2x2 Grid) */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          {environmentCards.map(card => (
            <div key={card.label} style={{ border: `1px solid ${card.caution ? '#FFE0D0' : '#EAEFEA'}`, borderRadius: 16, padding: '16px 14px', backgroundColor: '#FFFFFF' }}>
              <div style={{ fontSize: '1.2rem', marginBottom: 4 }}>{card.icon}</div>
              <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>{card.label}</div>
              <div style={{ fontSize: '0.96rem', fontWeight: 850, color: card.caution ? '#FF7F2B' : '#191F28', marginTop: 2 }}>{card.value}</div>
            </div>
          ))}
        </div>

        {/* Green Explanation Box: 왜 이렇게 분석했나요? */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 10 }}>
            왜 이렇게 분석했나요?
          </h3>
          <div style={{
            backgroundColor: '#E4F3E7',
            borderRadius: 16,
            padding: '16px 18px',
            border: '1px solid #D1EADB',
            fontSize: '0.86rem',
            color: '#154F36',
            fontWeight: 600,
            lineHeight: 1.6,
            wordBreak: 'keep-all',
            wordWrap: 'break-word'
          }}>
            {explanation}
          </div>
        </div>

        {/* 핵심 위험 List */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            핵심 위험
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {risks.length === 0 ? (
              <div style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.82rem', color: '#6E7671' }}>
                제공된 분석에는 핵심 위험 정보가 없습니다.
              </div>
            ) : risks.map((risk, index) => (
              <div key={`${risk.riskCode ?? risk.title ?? 'risk'}-${index}`} style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <AlertTriangle size={18} color="#FF7F2B" style={{ flexShrink: 0, marginTop: 2 }} />
                <div>
                  <strong style={{ fontSize: '0.9rem', color: '#191F28', fontWeight: 850, display: 'block', marginBottom: 2 }}>{risk.title}</strong>
                  <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>{risk.description || '상세 설명이 제공되지 않았습니다.'}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 시작 전 준비사항 */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            시작 전 준비사항
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {prePlantActions.length === 0 ? (
              <div style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.82rem', color: '#6E7671' }}>시작 전 준비 정보가 제공되지 않았습니다.</div>
            ) : prePlantActions.map((action, index) => (
              <div key={`${action.title ?? 'action'}-${index}`} style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.86rem', fontWeight: 750, color: '#191F28' }}>
                {action.title}
              </div>
            ))}
          </div>
        </div>

        {/* 현재 관리 포인트 (Green Box) */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 10 }}>
            현재 관리 포인트
          </h3>
          <div style={{
            backgroundColor: '#E4F3E7',
            borderRadius: 16,
            padding: '16px 18px',
            border: '1px solid #D1EADB',
            fontSize: '0.84rem',
            color: '#154F36',
            fontWeight: 600,
            lineHeight: 1.6,
            wordBreak: 'keep-all',
            wordWrap: 'break-word'
          }}>
            {growingActions.length === 0 ? (
              <>현재 관리 정보가 제공되지 않았습니다.</>
            ) : (
              <>
                <strong style={{ display: 'block', fontSize: '0.9rem', marginBottom: 4, fontWeight: 850 }}>{growingActions[0].title}</strong>
                {growingActions[0].reason || '상세 관리 설명이 제공되지 않았습니다.'}
              </>
            )}
          </div>
        </div>

        {/* Date Footer */}
        <div style={{ textAlign: 'right', fontSize: '0.74rem', color: '#8B95A1', fontWeight: 600, marginBottom: 20 }}>
          분석 기준일: {analysisDate}
        </div>

      </div>

      {/* Fixed Bottom CTA Button */}
      <div style={{
        padding: '16px 20px 32px 20px',
        backgroundColor: '#FFFFFF',
        borderTop: '1px solid #F0F2F1'
      }}>
        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-farm-primary"
          onClick={onRegisterCrop}
          style={{
            width: '100%',
            height: 56,
            fontSize: '1.05rem',
            borderRadius: 16
          }}
        >
          농작물 등록하기
        </motion.button>
      </div>
    </div>
  );
};
