import React from 'react';
import { motion } from 'framer-motion';

interface RegionRisksViewProps {
  onBack: () => void;
  onNext: () => void;
  onOpenAIChat: () => void;
}

export const RegionRisksView: React.FC<RegionRisksViewProps> = ({
  onBack,
  onNext
}) => {
  const risks = [
    {
      number: 1,
      title: '여름철 폭염',
      desc: '7~8월 평균 최고기온이 높은 편이라\n한낮 고온 대비가 필요해요.',
      links: ['농사로 공식자료', '원문 보기 →'],
      icon: '/svg-assets/weather/sunny.svg'
    },
    {
      number: 2,
      title: '장마철 집중호우',
      desc: '여름철 강수가 특정 시기에 몰려서\n배수 관리가 중요해요.',
      links: ['농사로 공식자료', '원문 보기 →'],
      icon: '/svg-assets/weather/rain.svg'
    },
    {
      number: 3,
      title: '일부 지역 배수 관리 필요',
      desc: '토성에 따라 배수가 느린 구역이 있어\n장마 전 점검이 필요해요.',
      links: ['농사로 공식자료', '원문 보기 →'],
      icon: '/svg-assets/ui-icons/warning-triangle.svg'
    }
  ];

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', padding: 0 }}>
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '24px 20px 24px 20px' }}>
        
        {/* Back button */}
        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: 24, display: 'flex' }}>
          <img src="/svg-assets/ui-icons/back.svg" alt="뒤로가기" style={{ width: 24, height: 24 }} />
        </button>

        {/* Title with mascot */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 32 }}>
          <h2 style={{ fontSize: '1.6rem', fontWeight: 900, color: '#191F28', lineHeight: 1.35, margin: 0 }}>
            이 지역의 핵심 환경<br />위험 <span style={{ color: '#2FA86A' }}>3가지</span>
          </h2>
          <img src="/svg-assets/brand/mascot/guide.svg" alt="마스코트" style={{ width: 56, height: 56, objectFit: 'contain', marginTop: -8 }} />
        </div>

        {/* Premium Risk Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {risks.map((risk, index) => (
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
                boxShadow: '0 4px 20px rgba(0, 0, 0, 0.02)',
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
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.04)', flexShrink: 0
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
                    {risk.links.map((link, i) => (
                      <button key={i} style={{
                        backgroundColor: i === 0 ? '#FFFFFF' : 'transparent',
                        border: i === 0 ? '1px solid #D1DFD7' : 'none',
                        borderRadius: 10, padding: i === 0 ? '7px 12px' : '7px 0',
                        fontSize: '0.78rem', fontWeight: 700,
                        color: i === 0 ? '#191F28' : '#8E9892', cursor: 'pointer',
                        display: 'flex', alignItems: 'center', gap: 4
                      }}>
                        {link}
                      </button>
                    ))}
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
