import React from 'react';
import { motion } from 'framer-motion';
import type { RegionReport } from '../../services/api';

interface RegionRisksViewProps {
  report?: RegionReport | null;
  onBack: () => void;
  onNext: () => void;
  onOpenAIChat: () => void;
}

export const RegionRisksView: React.FC<RegionRisksViewProps> = ({
  report,
  onBack,
  onNext
}) => {
  const rawRisks = report?.topRisks && report.topRisks.length > 0 ? report.topRisks : [
    {
      rank: 1,
      riskCode: 'HEAVY_RAIN',
      title: '장마철 집중호우 대비',
      description: '여름철 강수가 특정 시기에 몰려서 밭 주변 배수 관리가 중요해요.',
      source: { sourceUrl: 'https://www.nongsaro.go.kr' }
    }
  ];

  const dynamicRisks = rawRisks.map((r, idx) => ({
    number: r.rank || (idx + 1),
    title: r.title,
    desc: r.description,
    sourceUrl: r.source?.sourceUrl || 'https://www.nongsaro.go.kr',
    icon: r.riskCode === 'HEAVY_RAIN' ? '/svg-assets/weather/rain.svg' : r.riskCode === 'HEAT' ? '/svg-assets/weather/sunny.svg' : '/svg-assets/ui-icons/warning-triangle.svg'
  }));

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', padding: 0 }}>
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '32px 20px 24px 20px' }}>
        
        {/* Back button */}
        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: 24, display: 'flex' }}>
          <img src="/svg-assets/ui-icons/back.svg" alt="뒤로가기" style={{ width: 24, height: 24 }} />
        </button>

        {/* Dynamic Title based on actual identified risks count */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 32 }}>
          <h2 style={{ fontSize: '1.6rem', fontWeight: 900, color: '#191F28', lineHeight: 1.35, margin: 0 }}>
            이 지역의 핵심 환경<br />위험 <span style={{ color: '#2FA86A' }}>{dynamicRisks.length}가지</span>
          </h2>
          <img src="/svg-assets/brand/mascot/guide.svg" alt="마스코트" style={{ width: 56, height: 56, objectFit: 'contain', marginTop: -8 }} />
        </div>

        {/* Risk Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {dynamicRisks.map((risk, index) => (
            <motion.div 
              key={risk.number}
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1, duration: 0.4 }}
              style={{
                backgroundColor: '#F8FAF8',
                borderRadius: 20,
                padding: '20px',
                border: '1px solid #EAEFEA',
                position: 'relative',
                overflow: 'hidden'
              }}
            >
              {/* Decorative Number Badge */}
              <div style={{
                position: 'absolute', top: 0, right: 0,
                backgroundColor: '#EDF7ED', color: '#2FA86A',
                borderBottomLeftRadius: 16, padding: '6px 14px',
                fontSize: '0.85rem', fontWeight: 900
              }}>
                No.{risk.number}
              </div>

              <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
                <div style={{ 
                  width: 44, height: 44, borderRadius: 14, backgroundColor: '#FFFFFF',
                  border: '1px solid #EAEFEA',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0
                }}>
                  <img src={risk.icon} alt="아이콘" style={{ width: 24, height: 24 }} />
                </div>
                
                <div style={{ flex: 1, marginTop: 2 }}>
                  <h3 style={{ fontSize: '1.15rem', fontWeight: 850, color: '#191F28', margin: '0 0 6px 0', paddingRight: 40 }}>
                    {risk.title}
                  </h3>
                  <p style={{
                    fontSize: '0.85rem', color: '#6E7671', fontWeight: 500,
                    lineHeight: 1.55, whiteSpace: 'pre-line', margin: '0 0 16px 0'
                  }}>
                    {risk.desc}
                  </p>

                  {/* Action links */}
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <button
                      onClick={() => window.open('https://www.nongsaro.go.kr', '_blank')}
                      style={{
                        backgroundColor: '#FFFFFF',
                        border: '1px solid #D1DFD7',
                        borderRadius: 10, padding: '7px 12px',
                        fontSize: '0.78rem', fontWeight: 700,
                        color: '#191F28', cursor: 'pointer',
                        display: 'flex', alignItems: 'center', gap: 4
                      }}
                    >
                      농사로 공식자료
                    </button>
                    <button
                      onClick={() => window.open(risk.sourceUrl, '_blank')}
                      style={{
                        backgroundColor: 'transparent',
                        border: 'none',
                        borderRadius: 10, padding: '7px 0',
                        fontSize: '0.78rem', fontWeight: 700,
                        color: '#8E9892', cursor: 'pointer',
                        display: 'flex', alignItems: 'center', gap: 4
                      }}
                    >
                      원문 보기 →
                    </button>
                  </div>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Fixed Bottom CTA Button */}
      <div style={{ padding: '16px 20px 32px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F0F2F5' }}>
        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-farm-primary"
          onClick={onNext}
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16 }}
        >
          다음으로 진행하기
        </motion.button>
      </div>
    </div>
  );
};
