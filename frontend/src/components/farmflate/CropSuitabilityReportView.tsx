import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, CheckCircle, ShieldAlert } from 'lucide-react';
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
  cropName = '상추',
  score,
  report,
  onBack,
  onRegisterCrop
}) => {
  // 1. Flexible Crop Recommendation & Score Resolution
  const targetCropName = (cropName || '상추').trim();
  const crop = report?.recommendedCrops.find(item => {
    const name = item.cropName || '';
    return name === targetCropName || name.includes(targetCropName) || targetCropName.includes(name);
  }) ?? report?.recommendedCrops[0];

  const reportScore = score ?? crop?.score ?? report?.regionScore ?? 85;
  const numericScore = reportScore;

  // 2. Grade & Status Translation Maps
  const gradeLabels: Record<string, string> = {
    EXCELLENT: '최적',
    GOOD: '양호',
    MODERATE: '보통',
    CAUTION: '주의',
    HIGH: '위험',
    WARNING: '경고'
  };

  const translateGrade = (grade?: string | null, defaultScore?: number | null) => {
    if (grade && gradeLabels[grade.toUpperCase()]) {
      return gradeLabels[grade.toUpperCase()];
    }
    if (typeof defaultScore === 'number') {
      if (defaultScore >= 80) return '양호';
      if (defaultScore >= 60) return '보통';
      return '주의';
    }
    return '보통';
  };

  const environmentCards = [
    { icon: '🌦️', label: '기후 적합도', value: translateGrade(report?.components?.climate?.grade, report?.components?.climate?.score) },
    { icon: '🌱', label: '토양 적합도', value: translateGrade(report?.components?.soil?.grade, report?.components?.soil?.score) },
    { icon: '🏡', label: '재배 환경', value: translateGrade(report?.components?.cultivation?.grade, report?.components?.cultivation?.score) },
    { icon: '⚠️', label: '위험도 평가', value: translateGrade(report?.components?.hazard?.grade, report?.components?.hazard?.safetyScore), caution: true }
  ];

  // 3. Explanation & Reason Wording
  const explanation = crop?.positiveReasons?.[0]
    ?? crop?.cautionReason
    ?? report?.environmentFeatures?.[0]
    ?? `${report?.region?.sidoName || ''} ${report?.region?.sigunguName || ''}의 토양 성분과 기상청 실시간 예보 데이터를 기반으로 ${targetCropName} 생육 환경 적합도를 종합 분석했습니다.`;

  // 4. Risks & English Translation Engine
  const rawRisks = (report?.topRisks ?? []).slice(0, 3);

  const translateRiskTitle = (title?: string | null): string => {
    if (!title) return `${targetCropName} 생육 환경 주의사항`;
    const norm = title.toUpperCase();
    if (norm.includes('LETTUCE_HEAT_HUMIDITY') || (norm.includes('LETTUCE') && norm.includes('HEAT'))) return `${targetCropName} 고온·고습 생육 장애 위험`;
    if (norm.includes('HEAVY_RAIN') || norm.includes('RAIN')) return '집중호우 및 토양 과습 위험';
    if (norm.includes('HEAT') || norm.includes('HIGH_TEMP')) return '폭염 및 고온 생육 지연 위험';
    if (norm.includes('COLD') || norm.includes('FROST')) return '저온 및 늦서리 피해 위험';
    if (norm.includes('DRY') || norm.includes('DROUGHT')) return '가뭄 및 수분 부족 주의';
    if (norm.includes('HIGH_HUMIDITY') || norm.includes('HUMIDITY') || title.includes('고습')) return '고습 환경 곰팡이·병해충 주의';
    if (norm.includes('SOIL') || norm.includes('ACID')) return '토양 pH 산도 불균형 주의';
    return title;
  };

  const translateRiskDescription = (desc?: string | null): string => {
    if (!desc) return '해당 시기의 기상청 단기예보 및 토양 특성에 맞춰 적절한 수세 관리가 필요합니다.';
    if (desc.includes('lettuce heat exposure')) return '여름철 고온 및 높은 상대습도로 인해 작물 잎 무름 현상 및 무름병 발생 위험이 높아집니다.';
    if (desc.includes('high relative humidity')) return '상대습도가 높으면 작물의 증산작용이 저해되어 병해충 발생 확률이 크게 높아집니다.';
    if (desc.includes('high temperature')) return '지속적인 고온 노출 시 세포 손상 및 뿌리 호흡 장애가 우려되므로 차광 조치가 권장됩니다.';
    if (desc.includes('heavy rain')) return '집중호우 시 뿌리 썩음 예방을 위해 밭 두둑을 높이고 배수로를 정비하세요.';
    return desc;
  };

  const displayRisks = rawRisks.length > 0 ? rawRisks.map(r => ({
    title: translateRiskTitle(r.title),
    description: translateRiskDescription(r.description)
  })) : [
    {
      title: `${targetCropName} 고온·고습 생육 장애 위험`,
      description: '여름철 높은 기온과 과습 환경 시 잎 무름병 및 팁번 현상이 발생할 수 있으니 통풍에 신경 쓰세요.'
    },
    {
      title: '배수 및 토양 습도 관리',
      description: '집중호우에 대비해 배수로를 쇄신하고이랑(두둑) 높이를 확보하여 뿌리 과습을 예방하세요.'
    }
  ];

  // 5. Action Guidelines (시작 전 준비사항 & 현재 관리 포인트)
  const prePlantActions = (report?.prioritizedActions ?? []).filter(action => Boolean(action.stage && /(PRE|전|준비)/i.test(action.stage)));
  const growingActions = (report?.prioritizedActions ?? []).filter(action => Boolean(action.stage && /(CULT|재배|생육)/i.test(action.stage)));

  const displayPrePlant = prePlantActions.length > 0
    ? prePlantActions.map(a => a.title)
    : [
        `${targetCropName} 입식 전 토양 산도(pH 6.0~6.8) 및 유기물 함량 사전 정밀 점검`,
        `배수성이 양호한 높은 두둑 형성 및 정식 일자 기상청 단기예보 확인`
      ];

  const displayGrowingTitle = growingActions.length > 0
    ? growingActions[0].title
    : `${targetCropName} 생육기 고온 조절 및 차광막 설치 관리`;

  const displayGrowingReason = growingActions.length > 0
    ? (growingActions[0].reason || '적정 토양 수분 유지 및 차가운 수액 공급으로 수세를 보호하세요.')
    : '한낮 고온 시 차광막을 활용하고 차가운 수분을 엽면시비하여 작물의 온도를 낮춰주세요.';

  const analysisDate = report?.analyzedAt
    ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(report.analyzedAt))
    : new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date());

  // SVG Ring Gauge Calculations
  const radius = 64;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (circumference * Math.min(100, Math.max(0, numericScore))) / 100;

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
          marginBottom: 16
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
        <div style={{ fontSize: '0.94rem', fontWeight: 850, color: '#154F36', marginBottom: 20, textAlign: 'center' }}>
          {fieldName ? fieldName : '내 밭'} · {targetCropName}
        </div>

        {/* Circular Ring Gauge & Score */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: 24 }}>
          <span style={{ fontSize: '0.82rem', color: '#6E7671', fontWeight: 700, marginBottom: 12 }}>
            농작물 적합도 점수
          </span>

          <div style={{ position: 'relative', width: 160, height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
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
                {numericScore}
              </span>
              <span style={{ fontSize: '1.2rem', fontWeight: 800, color: '#2FA86A', marginLeft: 2 }}>점</span>
            </div>
          </div>

          <h2 style={{ fontSize: '1.02rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center', lineHeight: 1.4 }}>
            {numericScore >= 80 
              ? `${targetCropName} 재배에매우 적합한 농사 환경입니다!` 
              : `${targetCropName} 생육 조건을 만족하나 일부 기상 환경에 주의가 필요합니다.`
            }
          </h2>
        </div>

        {/* 4 Environment Status Cards (2x2 Grid) */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          {environmentCards.map(card => (
            <div key={card.label} style={{ border: `1px solid ${card.caution ? '#FFE0D0' : '#EAEFEA'}`, borderRadius: 16, padding: '16px 14px', backgroundColor: '#FFFFFF' }}>
              <div style={{ fontSize: '1.2rem', marginBottom: 4 }}>{card.icon}</div>
              <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>{card.label}</div>
              <div style={{ fontSize: '0.96rem', fontWeight: 850, color: card.caution ? '#FF7F2B' : '#154F36', marginTop: 2 }}>{card.value}</div>
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
            핵심 위험 요인
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {displayRisks.map((risk, index) => (
              <div key={index} style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <ShieldAlert size={20} color="#FF7F2B" style={{ flexShrink: 0, marginTop: 2 }} />
                <div>
                  <strong style={{ fontSize: '0.9rem', color: '#191F28', fontWeight: 850, display: 'block', marginBottom: 3, lineHeight: 1.3 }}>
                    {risk.title}
                  </strong>
                  <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500, lineHeight: 1.45, display: 'block' }}>
                    {risk.description}
                  </span>
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
            {displayPrePlant.map((actionText, index) => (
              <div key={index} style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.86rem', fontWeight: 750, color: '#191F28', display: 'flex', alignItems: 'center', gap: 10 }}>
                <CheckCircle size={16} color="#2FA86A" style={{ flexShrink: 0 }} />
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
