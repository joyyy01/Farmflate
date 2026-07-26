import React from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, AlertTriangle } from 'lucide-react';
import type { RegionReport } from '../../services/api';
import { formatReportLabel, formatReportText } from '../../utils/reportDisplay';
import { BackButton } from '../common/BackButton';

export interface RegionReportSummaryViewProps {
  regionName: string;
  report?: RegionReport | null;
  onBack: () => void;
  onNext: () => void;
  onOpenAIChat: () => void;
}

const summaryForRegionScore = (score: number | null): string | null => {
  if (typeof score !== 'number') return null;
  if (score >= 80) return '현재 조건에서 재배를 시작하기 좋은 환경입니다.';
  if (score >= 60) return '전반적으로 재배가 가능하지만 일부 환경 관리가 필요합니다.';
  if (score >= 40) return '재배 전 위험요인을 확인하고 보완 계획을 세워야 합니다.';
  return '현재 조건에서는 재배 부담이 크므로 추가 확인이 필요합니다.';
};

export const RegionReportSummaryView: React.FC<RegionReportSummaryViewProps> = ({
  regionName,
  report,
  onBack,
  onNext,
  onOpenAIChat: _onOpenAIChat
}) => {
  const score = report?.baseFitness ?? report?.regionScore ?? null;
  const hasScore = typeof score === 'number';
  const numericScore = hasScore ? score : 0;
  const displayScore = hasScore ? (Number.isInteger(score) ? String(score) : score.toFixed(1)) : null;
  const isPartialWithoutScore = report?.status === 'PARTIAL' && !hasScore;
  const cleanSummary = summaryForRegionScore(score) ?? formatReportText(report?.summary);
  const confidence = report?.dataConfidence;
  const legalDongCoverage = (report?.sources ?? [])
    .flatMap(source => source.transformations ?? [])
    .map(value => value.match(/^LEGAL_DONG_SAMPLE_COVERAGE(?:\[[^\]]+\])?:(\d+)\/(\d+)_OF_(\d+)$/))
    .find((match): match is RegExpMatchArray => match !== null);
  const legalDongSampleMessage = legalDongCoverage
    ? `대상 법정동 ${legalDongCoverage[3]}곳 중 대표 ${legalDongCoverage[2]}곳을 표본으로 확인했고, 공공 토양 자료가 있는 ${legalDongCoverage[1]}곳을 분석에 반영했어요.`
    : '토양 자료는 지역 단위 참고값으로 제공돼요. 실제 밭의 조건과 다를 수 있어요.';

  const climateGrade = formatReportLabel(report?.components?.climate?.grade ?? undefined);
  const soilGrade = formatReportLabel(report?.components?.soil?.grade ?? undefined);
  const hazardGrade = formatReportLabel(report?.components?.hazard?.grade ?? undefined);
  const cultivationGrade = formatReportLabel(report?.components?.cultivation?.grade ?? undefined);

  const crops = report?.recommendedCrops ?? [];

  // SVG Gauge Calculations
  const radius = 56;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (circumference * numericScore) / 100;

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
          <BackButton onClick={onBack} style={{ color: '#202A24' }} />
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
            기초 재배 적합도
          </div>

          {/* SVG Gauge Arc */}
          <div style={{
            position: 'relative', width: 152, height: 152,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            marginBottom: 16
          }}>
            <svg width="152" height="152" viewBox="0 0 144 144" style={{ transform: 'rotate(-90deg)', overflow: 'visible' }}>
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
            <div style={{ position: 'absolute', inset: 0, boxSizing: 'border-box', display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', whiteSpace: 'nowrap' }}>
              {hasScore ? (
                <>
                  <span style={{ fontSize: '1.85rem', fontWeight: 900, color: '#191F28', letterSpacing: '-0.05em', lineHeight: 1 }}>
                    {displayScore}
                  </span>
                  <span style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2FA86A', marginLeft: 3 }}>점</span>
                </>
              ) : (
                <div aria-label={isPartialWithoutScore ? '부분 분석 완료, 적합도 점수 자료 부족' : '적합도 점수 자료 부족'} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, maxWidth: 112 }}>
                  <strong style={{ fontSize: '0.92rem', fontWeight: 850, color: '#334155', lineHeight: 1.25 }}>
                    {isPartialWithoutScore ? '부분 분석 완료' : '점수 자료 부족'}
                  </strong>
                  <span style={{ fontSize: '0.68rem', fontWeight: 600, color: '#6E7671', lineHeight: 1.35 }}>
                    적합도 산출 자료가 제공되지 않았습니다.
                  </span>
                </div>
              )}
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
          <div style={{ width: '100%', marginTop: 12, padding: '12px 13px', borderRadius: 12, background: '#EFF8F1', color: '#405248' }}>
            <strong style={{ display: 'block', fontSize: '0.78rem', fontWeight: 800, color: '#267446', marginBottom: 7 }}>점수는 이렇게 읽어요</strong>
            <p style={{ margin: 0, fontSize: '0.75rem', lineHeight: 1.55 }}>토양과 평년 기후처럼 쉽게 바뀌지 않는 기본 조건이 이 지역에 얼마나 맞는지 보여줘요.</p>
            <p style={{ margin: '5px 0 0', fontSize: '0.75rem', lineHeight: 1.55 }}>앞으로의 날씨와 재배 시기가 지금 시작하기에 얼마나 알맞은지 보여줘요.</p>
          </div>
          <div style={{ width: '100%', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 12 }}>
            <div style={{ background: '#FFFFFF', borderRadius: 10, padding: '9px 10px' }}><span style={{ display: 'block', fontSize: '0.7rem', color: '#6E7671' }}>이번 계절 준비도</span><strong style={{ fontSize: '0.86rem', color: '#202A24' }}>{report?.seasonReadiness === null || report?.seasonReadiness === undefined ? '자료 부족' : `${report.seasonReadiness}점`}</strong></div>
            <div style={{ background: '#FFFFFF', borderRadius: 10, padding: '9px 10px' }}><span style={{ display: 'block', fontSize: '0.7rem', color: '#6E7671' }}>자료 신뢰도</span><strong style={{ fontSize: '0.86rem', color: '#202A24' }}>{confidence?.score === null || confidence?.score === undefined ? '자료 부족' : `${confidence.score}점`}</strong><span style={{ marginLeft: 4, fontSize: '0.68rem', color: '#6E7671' }}>{formatReportLabel(confidence?.level, '')}</span></div>
          </div>
          <p style={{ width: '100%', margin: '9px 0 0', color: '#59675F', fontSize: '0.73rem', lineHeight: 1.5 }}>자료 신뢰도는 공공 기상·토양 자료가 현재 지역을 얼마나 충분히 반영하는지 알려주는 참고값이에요.</p>
          <div style={{ width: '100%', marginTop: 10, padding: '11px 12px', border: '1px solid #E3EDE5', borderRadius: 12, background: '#FBFDFC' }}>
            <strong style={{ display: 'block', color: '#405248', fontSize: '0.76rem', fontWeight: 800, marginBottom: 5 }}>법정동 표본과 자료 범위</strong>
            <p style={{ margin: 0, color: '#59675F', fontSize: '0.74rem', lineHeight: 1.5 }}>{legalDongSampleMessage}</p>
            <p style={{ margin: '5px 0 0', color: '#6E7671', fontSize: '0.72rem', lineHeight: 1.5 }}>지역 참고값이므로 실제 밭의 토양검사 결과와 차이가 날 수 있어요.</p>
          </div>
          {confidence?.message && <p style={{ width: '100%', margin: '10px 0 0', color: '#A66B19', fontSize: '0.75rem', lineHeight: 1.45 }}>자료 해석 안내: {confidence.message}</p>}
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
            {(report?.environmentFeatures ?? []).length === 0 ? (
              <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10 }}><AlertTriangle size={18} color="#FF842F" /><span style={{ fontSize: '0.86rem', fontWeight: 650, color: '#6F7772' }}>환경 특징 자료가 제공되지 않았습니다.</span></div>
            ) : (report?.environmentFeatures ?? []).map((feature, index) => (
              <div key={`${feature}-${index}`} style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10 }}><CheckCircle2 size={18} color="#2FA86A" /><span style={{ fontSize: '0.86rem', fontWeight: 650, color: '#191F28', wordBreak: 'keep-all', wordWrap: 'break-word' }}>{formatReportText(feature)}</span></div>
            ))}
          </div>
        </div>

        {/* Recommended Crops TOP 3 */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            추천 작물 TOP 3
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {crops.length === 0 && <div style={{ color: '#6F7772', fontSize: '0.86rem' }}>추천 작물 자료가 제공되지 않았습니다.</div>}
            {crops.map((item, idx: number) => {
              const isFirst = idx === 0;
              const cropImg = item.iconUrl;
                const reasonText = formatReportText(item.positiveReasons[0] || item.cautionReason, '근거 자료가 제공되지 않았습니다.');
              
              return (
                <div key={idx} style={{
                  backgroundColor: '#FFFFFF', borderRadius: 18, border: isFirst ? '1.5px solid #2FA86A' : '1px solid #EAEFEA',
                  padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14, flex: 1, minWidth: 0 }}>
                    {cropImg && <img src={cropImg} alt={item.cropName ?? ''} style={{ width: 44, height: 44, objectFit: 'contain', flexShrink: 0 }} />}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '0.96rem', fontWeight: 900, color: isFirst ? '#154F36' : '#191F28', marginBottom: 3 }}>
                        {item.rank ?? idx + 1}위. {item.cropName ?? '작물명 자료 부족'}
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
                    {item.score === null || item.score === undefined ? '자료 부족' : <>{item.score}<span style={{ fontSize: '0.74rem', fontWeight: 700 }}>점</span></>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div style={{ borderTop: '1px solid #EEF1EF', paddingTop: 14, marginBottom: 12, color: '#6F7772', fontSize: '0.74rem', lineHeight: 1.5 }}>
          <strong style={{ color: '#4B574F' }}>데이터 근거</strong><br />
          {report?.sources[0] ? `${[report.sources[0].provider, report.sources[0].service].filter(Boolean).join(' / ') || '제공자 정보 없음'} · ${report.sources[0].dataDate ?? '날짜 정보 없음'}` : '출처 정보가 제공되지 않았습니다.'}
          {report?.sources.some(source => source.isFallback) ? ' · 대체 데이터 포함' : ''}
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
