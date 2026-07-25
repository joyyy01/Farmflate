import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, Share2, CheckCircle, AlertTriangle } from 'lucide-react';
import type { FieldSuitabilityPreview } from '../../types/report';

interface CropSuitabilityReportViewProps {
  fieldName?: string;
  cropName?: string;
  fieldPreview?: FieldSuitabilityPreview | null;
  onBack: () => void;
  onRegisterCrop: () => void;
  onOpenTips: () => void;
}

const gradeLabels: Record<string, string> = {
  EXCELLENT: '최적',
  VERY_GOOD: '양호',
  GOOD: '양호',
  MODERATE: '보통',
  CAUTION: '주의',
  HIGH: '위험',
  WARNING: '경고',
  UNAVAILABLE: '자료 부족'
};

const translateGrade = (grade?: string | null): string => {
  if (grade && gradeLabels[grade.toUpperCase()]) return gradeLabels[grade.toUpperCase()];
  return '자료 부족';
};

const statusLabels: Record<string, string> = {
  GOOD: '양호',
  CAUTION: '주의',
  RISK: '위험',
  UNAVAILABLE: '자료 부족',
  INPUT_RECORDED: '입력 기준'
};

const translateStatus = (status?: string | null): string => {
  if (status && statusLabels[status.toUpperCase()]) return statusLabels[status.toUpperCase()];
  return '자료 부족';
};

export const CropSuitabilityReportView: React.FC<CropSuitabilityReportViewProps> = ({
  fieldName = '우리집 텃밭',
  cropName = '상추',
  fieldPreview,
  onBack,
  onRegisterCrop,
  onOpenTips
}) => {
  const suitability = fieldPreview?.suitabilityReport;
  const targetCropName = (cropName || '상추').trim();

  const numericScore = suitability?.suitabilityScore ?? null;
  const grade = suitability?.grade ?? null;

  // Environment cards from suitabilityReport.conditions
  const conditionByKey = (key: string) =>
    suitability?.conditions?.find(c => c.key?.toUpperCase() === key) ?? null;

  const climateCondition = conditionByKey('CLIMATE');
  const soilCondition = conditionByKey('SOIL');
  const cultivationCondition = conditionByKey('CULTIVATION');
  const hazardCondition = conditionByKey('NATURAL_HAZARD');

  const environmentCards = [
    { icon: '/svg-assets/report/category/climate.svg', label: '기후 적합도', value: climateCondition ? translateStatus(climateCondition.status) : '자료 부족' },
    { icon: '/svg-assets/report/category/soil.svg', label: '토양 적합도', value: soilCondition ? translateStatus(soilCondition.status) : '자료 부족' },
    { icon: '/svg-assets/report/category/greenhouse.svg', label: '재배 환경', value: cultivationCondition ? translateStatus(cultivationCondition.status) : '자료 부족' },
    { icon: '/svg-assets/report/category/warning.svg', label: '위험도 평가', value: hazardCondition ? translateStatus(hazardCondition.status) : '자료 부족', caution: true }
  ];

  // Explanation from suitabilityReport.summary
  const explanation = suitability?.summary ?? '자료 부족';

  // Risks from suitabilityReport.keyRisks
  const displayRisks = (suitability?.keyRisks ?? []).slice(0, 3).map(r => ({
    title: r.title ?? `${targetCropName} 생육 환경 주의사항`,
    description: r.description ?? (r.actions && r.actions.length > 0 ? r.actions.join(', ') : '해당 시기의 기상 조건에 맞춰 적절한 관리가 필요합니다.')
  }));

  // Pre-plant checklist from suitabilityReport.prePlantChecklist
  const displayPrePlant: string[] = (suitability?.prePlantChecklist ?? []).filter(
    (t): t is string => typeof t === 'string' && t.trim().length > 0
  );
  if (displayPrePlant.length === 0) {
    displayPrePlant.push('자료 부족 — 재배 시작 전 필지 상태를 현장에서 확인하세요.');
  }

  // Current management points from suitabilityReport.currentManagementPoints
  const managementPoints = suitability?.currentManagementPoints ?? [];
  const displayGrowingTitle = managementPoints.length > 0 ? managementPoints[0] : '자료 부족';
  const displayGrowingReason = managementPoints.length > 1
    ? managementPoints.slice(1).join(', ')
    : (managementPoints.length === 1 ? '' : '추가 관리 포인트 자료가 없습니다.');

  // Analysis basis date from suitabilityReport
  const analysisDate = suitability?.analysisBasisDate
    ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(suitability.analysisBasisDate))
    : '자료 부족';

  // Crop mascot icon lookup (best-effort visual only; falls back to a generic sprout)
  const cropIconMap: Record<string, string> = {
    '상추': 'lettuce',
    '감자': 'potato',
    '토마토': 'tomato',
    '오이': 'cucumber',
    '고추': 'pepper',
    '배추': 'cabbage',
    '사과': 'apple',
    '배': 'pear'
  };
  const matchedCropKey = Object.keys(cropIconMap).find(key => targetCropName.includes(key));
  const cropIconSrc = `/svg-assets/crops/${matchedCropKey ? cropIconMap[matchedCropKey] : 'sprout'}.svg`;

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#F4FBF5', display: 'flex', flexDirection: 'column', height: '100%', position: 'relative', overflow: 'hidden' }}>

      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '0 20px 100px 20px' }}>

        {/* Header */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '30px 1fr 30px',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #ecefed',
          marginBottom: 16
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202a24', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202a24', margin: 0, textAlign: 'center' }}>
            농작물 적합도 리포트
          </h1>
          <button style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202a24', padding: 0, justifySelf: 'end' }}>
            <Share2 size={20} />
          </button>
        </div>

        {/* Field & Crop Subtitle */}
        <div style={{ fontSize: '0.9rem', fontWeight: 850, color: '#25804b', marginBottom: 18, textAlign: 'center' }}>
          {fieldName} · {targetCropName}
        </div>

        {/* Donut Ring & Score & Crop Mascot */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', position: 'relative', marginBottom: 24 }}>
          <div style={{ fontSize: '0.78rem', color: '#626a65', marginBottom: 12 }}>
            농작물 적합도 점수
          </div>

          <div style={{
            position: 'relative',
            width: 164,
            height: 164,
            borderRadius: '50%',
            background: 'conic-gradient(from -18deg, #88dc63 0 34%, #61d28c 34% 64%, #66cfe0 64% 83%, #7bdc70 83% 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 10
          }}>
            <div style={{
              width: 137,
              height: 137,
              borderRadius: '50%',
              backgroundColor: '#FFFFFF',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <span style={{ fontSize: '3.2rem', fontWeight: 900, color: '#145b39', lineHeight: 1 }}>
                {numericScore !== null ? numericScore : '—'}
              </span>
              {numericScore !== null && (
                <small style={{ color: '#848985', fontSize: '0.86rem', fontWeight: 500, marginTop: 2 }}>/100</small>
              )}
            </div>

            {/* Crop Mascot */}
            <img
              src={cropIconSrc}
              alt={`${targetCropName} 캐릭터`}
              style={{
                position: 'absolute',
                right: -28,
                bottom: -4,
                width: 86,
                height: 74,
                objectFit: 'contain'
              }}
            />
          </div>

          <div style={{ fontSize: '0.9rem', fontWeight: 850, color: '#202a24', marginTop: 8, textAlign: 'center', lineHeight: 1.4 }}>
            {numericScore !== null
              ? (numericScore >= 80
                  ? `✨ 지금 ${targetCropName} 재배를 시작하기 좋은 환경이에요`
                  : `${targetCropName} 생육 조건을 만족하나 일부 기상 환경에 주의가 필요해요`)
              : '적합도 점수 자료가 없습니다'
            }
          </div>

          {grade && (
            <div style={{ fontSize: '0.82rem', fontWeight: 700, color: '#25804b', marginTop: 4 }}>
              등급: {translateGrade(grade)}
            </div>
          )}
        </div>

        {/* 4 Environment Status Cards */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          {environmentCards.map(card => (
            <div
              key={card.label}
              style={
                card.caution
                  ? { border: '1px solid #ffd9b7', borderRadius: 12, padding: '12px 14px', background: 'linear-gradient(135deg, #fffdfa, #fff8f0)', display: 'flex', alignItems: 'center', gap: 10 }
                  : { border: '1px solid #e1e5e2', borderRadius: 12, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }
              }
            >
              <img src={card.icon} alt={card.label} style={{ width: 39, height: 39, objectFit: 'contain' }} />
              <div>
                <div style={{ fontSize: '0.7rem', color: '#747a76' }}>{card.label}</div>
                <div style={{ fontSize: '0.86rem', fontWeight: 850, color: card.caution ? '#ff7d32' : '#309159', marginTop: 2 }}>{card.value}</div>
              </div>
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
        {displayRisks.length > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#202a24', marginBottom: 10 }}>
              핵심 위험 요인
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {displayRisks.map((risk, index) => (
                <div key={index} style={{ backgroundColor: '#f6f7f6', borderRadius: 10, padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
                  <AlertTriangle size={16} color="#ff892f" style={{ flexShrink: 0 }} />
                  <div>
                    <strong style={{ display: 'block', fontSize: '0.78rem', color: '#202a24' }}>
                      {risk.title}
                    </strong>
                    <span style={{ fontSize: '0.68rem', color: '#818783' }}>
                      {risk.description}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 시작 전 준비사항 */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#202a24', marginBottom: 10 }}>
            시작 전 준비사항
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
            {displayPrePlant.map((actionText: string, index: number) => (
              <div key={index} style={{ border: '1px solid #e1e5e2', borderRadius: 11, backgroundColor: '#FFFFFF', textAlign: 'center', padding: '12px 8px', fontSize: '0.72rem', color: '#5e6560', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                <CheckCircle size={22} color="#2FA86A" />
                <span>{actionText}</span>
              </div>
            ))}
          </div>
        </div>

        {/* 현재 관리 포인트 (Green Box) */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 10 }}>
            현재 생육 관리 포인트
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
            <strong style={{ display: 'block', fontSize: '0.9rem', marginBottom: 4, fontWeight: 850 }}>
              {displayGrowingTitle}
            </strong>
            {displayGrowingReason}
          </div>
        </div>

        {/* Date Footer (plain text, bottom of scroll content) */}
        <div style={{ textAlign: 'center', fontSize: '0.7rem', color: '#84908a' }}>
          분석 기준일: {analysisDate}
        </div>

      </div>

      {/* Fixed Bottom CTA Buttons */}
      <div style={{
        padding: '16px 20px 32px 20px',
        backgroundColor: '#FFFFFF',
        borderTop: '1px solid #F0F2F1'
      }}>
        <motion.button
          whileTap={{ scale: 0.97 }}
          onClick={onRegisterCrop}
          style={{
            width: '100%',
            height: 48,
            borderRadius: 24,
            background: 'linear-gradient(135deg, #2e9f5b, #39a965)',
            color: '#FFFFFF',
            fontSize: '1rem',
            fontWeight: 850,
            border: 'none',
            cursor: 'pointer',
            boxShadow: '0 8px 18px rgba(47, 154, 88, 0.16)',
            marginBottom: 12
          }}
        >
          농작물 등록하기
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.97 }}
          onClick={onOpenTips}
          style={{
            width: '100%',
            height: 48,
            borderRadius: 24,
            background: 'linear-gradient(90deg, #e7f6e9, #eef9ef)',
            color: '#25804b',
            fontSize: '1rem',
            fontWeight: 850,
            border: 'none',
            cursor: 'pointer'
          }}
        >
          해당 지역 농사 TIP 보러가기
        </motion.button>
      </div>
    </div>
  );
};
