import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, CheckCircle2, AlertTriangle } from 'lucide-react';
import type { RegionReport } from '../../services/api';

export interface RegionReportSummaryViewProps {
  regionName: string;
  report?: RegionReport | null;
  onBack: () => void;
  onNext: () => void;
  onOpenAIChat: () => void;
}

export const RegionReportSummaryView: React.FC<RegionReportSummaryViewProps> = ({
  regionName,
  report,
  onBack,
  onNext,
  onOpenAIChat: _onOpenAIChat
}) => {
  const score = report?.regionScore ?? 82;

  // Extract short region name (e.g., "군산시" from "전북특별자치도 군산시")
  const shortRegionName = regionName.split(' ').pop() || regionName;

  // Helper function to attach natural Korean particles (은/는) based on Hangul batchim
  const getSubjectName = (name: string): string => {
    if (!name) return '이 지역은';
    const lastChar = name.charCodeAt(name.length - 1);
    if (lastChar >= 0xAC00 && lastChar <= 0xD7A3) {
      const hasJongsung = (lastChar - 0xAC00) % 28 !== 0;
      return `${name}${hasJongsung ? '은' : '는'}`;
    }
    return `${name}는`;
  };

  const cleanSubject = getSubjectName(shortRegionName);

  // Clean, human-phrased summary sentence without any awkward AI template syntax
  const rawSummary = report?.summary || '';
  const cleanSummary = rawSummary
    ? rawSummary.replace(/고창군은\(는\)|고창군은|고창군은\(는\)|[가-힣]+은\(는\)/g, cleanSubject)
    : `${cleanSubject} 현재 계절에 여러 작물을 재배하기 양호한 환경이지만, 배수 관리에 신경 써주세요.`;

  // Translate English grades (GOOD, CAUTION, WARNING) to Korean (양호, 주의, 위험)
  const formatGrade = (grade?: string) => {
    if (!grade) return '양호';
    if (grade === 'GOOD' || grade === 'EXCELLENT') return '양호';
    if (grade === 'CAUTION' || grade === 'FAIR') return '주의';
    if (grade === 'WARNING' || grade === 'POOR') return '위험';
    return grade;
  };

  const climateGrade = formatGrade(report?.components?.climate?.grade);
  const soilGrade = formatGrade(report?.components?.soil?.grade);
  const hazardGrade = formatGrade(report?.components?.hazard?.grade);
  const cultivationGrade = formatGrade(report?.components?.cultivation?.grade);

  const getCropIcon = (cropName: string) => {
    if (cropName.includes('상추')) return '/svg-assets/crops/lettuce.svg';
    if (cropName.includes('사과')) return '/svg-assets/crops/apple.svg';
    if (cropName.includes('배')) return '/svg-assets/crops/pear.svg';
    if (cropName.includes('고추')) return '/svg-assets/crops/pepper.svg';
    if (cropName.includes('토마토')) return '/svg-assets/crops/tomato.svg';
    if (cropName.includes('배추')) return '/svg-assets/crops/cabbage.svg';
    if (cropName.includes('오이')) return '/svg-assets/crops/cucumber.svg';
    return '/svg-assets/crops/potato.svg';
  };

  const crops = report?.recommendedCrops && report.recommendedCrops.length > 0 ? report.recommendedCrops : [
    { rank: 1, cropName: '감자', score: Math.min(score + 5, 98), positiveReasons: ['서늘한 기후 조건과 배수가 우수한 토양 생육 환경에 잘 맞습니다.'] },
    { rank: 2, cropName: '상추', score: score + 1, positiveReasons: ['토양 유기물 함량과 배수 상태가 우수합니다.'] },
    { rank: 3, cropName: '사과', score: score - 6, positiveReasons: ['풍부한 일조시간과 기후 일교차 조건이 부합해요.'] }
  ];

  // SVG Gauge Calculations
  const radius = 56;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (circumference * Math.min(score, 100)) / 100;

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', position: 'relative', overflow: 'hidden' }}>
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '0 20px 100px 20px', display: 'flex', flexDirection: 'column' }}>
        
        {/* Header */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '30px 1fr 30px',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #F0F2F1',
          marginBottom: 20
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202A24', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            지역 환경 리포트
          </h1>
          <div />
        </div>

        {/* Region Name Badge & Mascot Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
          <div>
            <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 600 }}>분석 대상 지역</span>
            <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', margin: '4px 0 0 0', letterSpacing: '-0.02em' }}>
              {regionName}
            </h2>
          </div>
          <img
            src="/svg-assets/brand/mascot/cheer.svg"
            alt="마스코트"
            style={{ width: 58, height: 58, objectFit: 'contain', marginTop: -4 }}
          />
        </div>

        {/* Clean Score Card */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
          style={{
            backgroundColor: '#F8FAF8',
            borderRadius: 24,
            padding: '24px 20px',
            marginBottom: 24,
            border: '1px solid #E3EFE6',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center'
          }}
        >
          {/* Category Pill Tag */}
          <div style={{
            backgroundColor: '#E9F7EC', color: '#2FA86A',
            borderRadius: 14, padding: '4px 14px',
            fontSize: '0.78rem', fontWeight: 800, marginBottom: 16
          }}>
            농사 환경 종합 점수
          </div>

          {/* SVG Gauge Arc */}
          <div style={{
            position: 'relative', width: 144, height: 144,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            marginBottom: 16
          }}>
            <svg width="144" height="144" viewBox="0 0 144 144" style={{ transform: 'rotate(-90deg)' }}>
              <circle
                cx="72"
                cy="72"
                r={radius}
                stroke="#EAEFEA"
                strokeWidth="8"
                fill="transparent"
              />
              <circle
                cx="72"
                cy="72"
                r={radius}
                stroke="#2FA86A"
                strokeWidth="8"
                fill="transparent"
                strokeDasharray={circumference}
                strokeDashoffset={strokeDashoffset}
                strokeLinecap="round"
                style={{ transition: 'stroke-dashoffset 0.9s cubic-bezier(0.16, 1, 0.3, 1)' }}
              />
            </svg>

            {/* Score Typography */}
            <div style={{ position: 'absolute', display: 'flex', alignItems: 'baseline', gap: 2 }}>
              <span style={{ fontSize: '3.1rem', fontWeight: 900, color: '#191F28', letterSpacing: '-0.05em', lineHeight: 1 }}>
                {score}
              </span>
              <span style={{ fontSize: '1.1rem', fontWeight: 800, color: '#2FA86A' }}>
                점
              </span>
            </div>
          </div>

          {/* Natural Human Korean Summary */}
          <p style={{
            fontSize: '0.92rem',
            color: '#333D4B',
            fontWeight: 600,
            textAlign: 'center',
            lineHeight: 1.6,
            margin: 0,
            paddingTop: 14,
            borderTop: '1px solid #F0F4F1',
            width: '100%',
            wordBreak: 'keep-all',
            wordWrap: 'break-word',
            whiteSpace: 'pre-line'
          }}>
            {cleanSummary}
          </p>
        </motion.div>

        {/* 4 Category Status Chips */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          <div style={{ border: '1px solid #EAEFEA', borderRadius: 16, padding: '14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 12 }}>
            <img src="/svg-assets/report/category/climate.svg" alt="기후" style={{ width: 36, height: 36, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>기후 환경</div>
              <div style={{ fontSize: '0.9rem', fontWeight: 850, color: climateGrade === '위험' ? '#FF4D4F' : climateGrade === '주의' ? '#FF842F' : '#2FA86A', marginTop: 2 }}>{climateGrade}</div>
            </div>
          </div>

          <div style={{ border: '1px solid #EAEFEA', borderRadius: 16, padding: '14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 12 }}>
            <img src="/svg-assets/report/category/soil.svg" alt="토양" style={{ width: 36, height: 36, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>토양 환경</div>
              <div style={{ fontSize: '0.9rem', fontWeight: 850, color: soilGrade === '위험' ? '#FF4D4F' : soilGrade === '주의' ? '#FF842F' : '#2FA86A', marginTop: 2 }}>{soilGrade}</div>
            </div>
          </div>

          <div style={{ border: '1px solid #FFEBE0', borderRadius: 16, padding: '14px', backgroundColor: '#FFFDFB', display: 'flex', alignItems: 'center', gap: 12 }}>
            <img src="/svg-assets/report/category/warning.svg" alt="자연재해" style={{ width: 36, height: 36, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>자연재해</div>
              <div style={{ fontSize: '0.9rem', fontWeight: 850, color: hazardGrade === '위험' ? '#FF4D4F' : hazardGrade === '주의' ? '#FF842F' : '#2FA86A', marginTop: 2 }}>{hazardGrade}</div>
            </div>
          </div>

          <div style={{ border: '1px solid #EAEFEA', borderRadius: 16, padding: '14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 12 }}>
            <img src="/svg-assets/report/category/greenhouse.svg" alt="재배 환경" style={{ width: 36, height: 36, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>재배 환경</div>
              <div style={{ fontSize: '0.9rem', fontWeight: 850, color: cultivationGrade === '위험' ? '#FF4D4F' : cultivationGrade === '주의' ? '#FF842F' : '#2FA86A', marginTop: 2 }}>{cultivationGrade}</div>
            </div>
          </div>
        </div>

        {/* Environmental Features Checklist */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            환경 특징
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <CheckCircle2 size={18} color="#2FA86A" />
              <span style={{ fontSize: '0.86rem', fontWeight: 650, color: '#191F28', wordBreak: 'keep-all', wordWrap: 'break-word' }}>{shortRegionName} 대표 토양 pH가 적정 산성도 범주입니다.</span>
            </div>
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <CheckCircle2 size={18} color="#2FA86A" />
              <span style={{ fontSize: '0.86rem', fontWeight: 650, color: '#191F28', wordBreak: 'keep-all', wordWrap: 'break-word' }}>계절별 일조시간 및 기후가 원활한 생육을 지원해요.</span>
            </div>
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <AlertTriangle size={18} color="#FF842F" />
              <span style={{ fontSize: '0.86rem', fontWeight: 650, color: '#191F28', wordBreak: 'keep-all', wordWrap: 'break-word' }}>여름철 집중호우 시 배수로 점검이 필수적입니다.</span>
            </div>
          </div>
        </div>

        {/* Recommended Crops TOP 3 */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            추천 작물 TOP 3
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {crops.map((item: any, idx: number) => {
              const isFirst = idx === 0;
              const cropImg = getCropIcon(item.cropName);
              const reasonText = item.positiveReasons?.[0] || '지역 환경 적합도가 우수함';
              
              return (
                <div key={idx} style={{
                  backgroundColor: '#FFFFFF', borderRadius: 18, border: isFirst ? '1.5px solid #2FA86A' : '1px solid #EAEFEA',
                  padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14, flex: 1, minWidth: 0 }}>
                    <img src={cropImg} alt={item.cropName} style={{ width: 44, height: 44, objectFit: 'contain', flexShrink: 0 }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '0.96rem', fontWeight: 900, color: isFirst ? '#154F36' : '#191F28', marginBottom: 3 }}>
                        {item.rank || idx + 1}위. {item.cropName}
                      </div>
                      <div style={{
                        fontSize: '0.78rem', color: '#6E7671', fontWeight: 500,
                        wordBreak: 'keep-all', wordWrap: 'break-word', lineHeight: 1.45
                      }}>
                        {reasonText}
                      </div>
                    </div>
                  </div>
                  
                  <div style={{
                    backgroundColor: isFirst ? '#E9F7EC' : '#F8FAF8',
                    color: isFirst ? '#2FA86A' : '#191F28',
                    padding: '6px 12px', borderRadius: 12,
                    fontSize: '0.88rem', fontWeight: 900,
                    whiteSpace: 'nowrap', flexShrink: 0,
                    display: 'inline-flex', alignItems: 'baseline', gap: 1
                  }}>
                    {item.score}<span style={{ fontSize: '0.74rem', fontWeight: 700 }}>점</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

      </div>

      {/* Fixed Bottom CTA Button */}
      <div style={{ padding: '16px 20px 32px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F0F2F1' }}>
        <motion.button
          whileTap={{ scale: 0.98 }}
          onClick={onNext}
          className="btn-farm-primary"
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16 }}
        >
          다음
        </motion.button>
      </div>
    </div>
  );
};
