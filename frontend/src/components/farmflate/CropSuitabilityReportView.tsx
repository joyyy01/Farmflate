import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, Share2, CheckCircle, AlertTriangle } from 'lucide-react';
import type { RegionReport } from '../../services/api';
import type { FieldProfile } from '../../types/report';

interface CropSuitabilityReportViewProps {
  fieldName?: string;
  cropName?: string;
  score?: number | null;
  report?: RegionReport | null;
  fieldPreview?: FieldProfile | null;
  onBack: () => void;
  onRegisterCrop: () => void;
  onOpenTips: () => void;
}

export const CropSuitabilityReportView: React.FC<CropSuitabilityReportViewProps> = ({
  fieldName = '우리집 텃밭',
  cropName = '상추',
  score,
  report,
  fieldPreview,
  onBack,
  onRegisterCrop,
  onOpenTips
}) => {
  // Prefer server preview data when available
  const previewSuitability = fieldPreview?.suitabilityReport;
  const targetCropName = (cropName || '상추').trim();
  const crop = report?.recommendedCrops?.find(item => {
    const name = item?.cropName ?? '';
    return name === targetCropName || name.includes(targetCropName) || targetCropName.includes(name);
  }) ?? report?.cropResults?.find(item => {
    const name = item?.cropName ?? '';
    return name === targetCropName || name.includes(targetCropName) || targetCropName.includes(name);
  }) ?? report?.recommendedCrops?.[0];

  const numericScore = previewSuitability?.suitabilityScore ?? score ?? crop?.score ?? report?.regionScore ?? null;

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
    { icon: '/svg-assets/report/category/climate.svg', label: '기후 적합도', value: translateGrade(report?.components?.climate?.grade, report?.components?.climate?.score) },
    { icon: '/svg-assets/report/category/soil.svg', label: '토양 적합도', value: translateGrade(report?.components?.soil?.grade, report?.components?.soil?.score) },
    { icon: '/svg-assets/report/category/greenhouse.svg', label: '재배 환경', value: translateGrade(report?.components?.cultivation?.grade, report?.components?.cultivation?.score) },
    { icon: '/svg-assets/report/category/warning.svg', label: '위험도 평가', value: translateGrade(report?.components?.hazard?.grade, report?.components?.hazard?.safetyScore), caution: true }
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
                {numericScore !== null ? numericScore : '분석중'}
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
              : '서버 DB 데이터를 기반으로 적합도를 분석하고 있어요'
            }
          </div>
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
