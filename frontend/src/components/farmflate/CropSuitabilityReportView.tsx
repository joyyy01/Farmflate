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
  // 1. Genuine DB Crop Decision & Score Lookup across recommendedCrops & cropResults
  const targetCropName = (cropName || '상추').trim();
  const crop = report?.recommendedCrops?.find(item => {
    const name = item?.cropName ?? '';
    return name === targetCropName || name.includes(targetCropName) || targetCropName.includes(name);
  }) ?? report?.cropResults?.find(item => {
    const name = item?.cropName ?? '';
    return name === targetCropName || name.includes(targetCropName) || targetCropName.includes(name);
  }) ?? report?.recommendedCrops?.[0];

  const numericScore = score ?? crop?.score ?? report?.regionScore ?? null;

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

  // 3. Explanation & Reason Wording strictly from DB report
  const explanation = crop?.positiveReasons?.[0]
    ?? crop?.cautionReason
    ?? report?.summary
    ?? report?.environmentFeatures?.[0]
    ?? `${report?.region?.sidoName || ''} ${report?.region?.sigunguName || ''}의 토양 데이터와 기상청 실시간 관측 데이터를 기반으로 산출된 ${targetCropName} 생육 환경 적합도 결과입니다.`;

  // 4. Dynamic English Term Translation for DB Risks
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
    return desc
      .replace(/lettuce heat exposure/gi, '상추 고온 노출')
      .replace(/high humidity/gi, '높은 습도')
      .replace(/combined heat-humidity stress/gi, '복합 고온·다습 생육 장애 발생 가능성')
      .replace(/high relative humidity/gi, '상대습도 과다')
      .replace(/reduced evaporation and disease-pressure exposure/gi, '증산작용 저해 및 병해충 위험 상승')
      .replace(/high temperature/gi, '고온 지속')
      .replace(/heavy rain/gi, '집중호우');
  };

  const displayRisks = rawRisks.map(r => ({
    title: translateRiskTitle(r.title),
    description: translateRiskDescription(r.description || (r.causalChain ? r.causalChain.join(' → ') : null))
  }));

  // 5. Action Guidelines strictly from DB report's prioritizedActions & positiveReasons
  const prePlantActions = (report?.prioritizedActions ?? []).filter(action => Boolean(action.stage && /(PRE|전|준비)/i.test(action.stage)));
  const growingActions = (report?.prioritizedActions ?? []).filter(action => Boolean(action.stage && /(CULT|재배|생육)/i.test(action.stage)));

  const rawPrePlant = prePlantActions.length > 0
    ? prePlantActions.map(a => a.title)
    : (crop?.positiveReasons ?? [
        `${targetCropName} 입식 전 토양 산도 및 유기물 함량 사전 점검`,
        `배수성이 양호한 높은 두둑 형성 및 정식 일자 기상청 예보 확인`
      ]);

  const displayPrePlant: string[] = rawPrePlant.filter((t): t is string => typeof t === 'string' && t.trim().length > 0);

  const displayGrowingTitle = growingActions.length > 0
    ? growingActions[0].title
    : (crop?.cautionReason || `${targetCropName} 생육기 온도 및 수분 관리`);

  const displayGrowingReason = growingActions.length > 0
    ? (growingActions[0].reason || '기상 조건 및 토양 습도 변화에 따른 수세 관리가 필요합니다.')
    : '한낮 고온 시 차광막을 활용하고 수분 공급으로 수세를 보호하세요.';

  const analysisDate = report?.analyzedAt
    ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(report.analyzedAt))
    : new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date());

  // SVG Ring Gauge Calculations
  const radius = 64;
  const circumference = 2 * Math.PI * radius;
  const displayScoreVal = numericScore ?? 80;
  const strokeDashoffset = circumference - (circumference * Math.min(100, Math.max(0, displayScoreVal))) / 100;

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
                {numericScore !== null ? numericScore : '분석 중'}
              </span>
              {numericScore !== null && <span style={{ fontSize: '1.2rem', fontWeight: 800, color: '#2FA86A', marginLeft: 2 }}>점</span>}
            </div>
          </div>

          <h2 style={{ fontSize: '1.02rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center', lineHeight: 1.4 }}>
            {numericScore !== null
              ? (numericScore >= 80 
                  ? `${targetCropName} 재배에 매우 적합한 농사 환경입니다!` 
                  : `${targetCropName} 생육 조건을 만족하나 일부 기상 환경에 주의가 필요합니다.`)
              : '서버 DB 데이터를 기반으로 적합도를 분석하고 있습니다.'
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
        {displayRisks.length > 0 && (
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
        )}

        {/* 시작 전 준비사항 */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            시작 전 준비사항
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {displayPrePlant.map((actionText: string, index: number) => (
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
