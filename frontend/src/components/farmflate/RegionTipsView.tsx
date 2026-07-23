import React from 'react';
import { motion } from 'framer-motion';

interface RegionTipsViewProps {
  districtName: string;
  onGoToCreateField: () => void;
  onGoToRecommendedCrops: () => void;
}

export const RegionTipsView: React.FC<RegionTipsViewProps> = ({
  districtName,
  onGoToCreateField,
  onGoToRecommendedCrops
}) => {
  const tips = [
    {
      icon: '/svg-assets/ui-icons/droplet.svg',
      title: '장마철 배수 관리가 중요해요',
      desc: '이 지역은 여름철 강수가 몰리는 편이라\n밭 주변 배수로 점검이 필요해요.',
      link: '농사로 원문 보기 →'
    },
    {
      icon: '/svg-assets/weather/sunny.svg',
      title: '여름철 고온에 대비해주세요',
      desc: '한낮 최고기온이 높은 날이 많아\n시설재배 시 환기 관리가 중요해요.',
      link: '농사로 원문 보기 →'
    },
    {
      icon: '/svg-assets/ui-icons/search.svg',
      title: '토양검정을 진행하면 더 정확한\n분석이 가능해요',
      desc: '지역 평균값 대신 내 밭의 실제 토양\n상태를 반영할 수 있어요.',
      link: '토양검정 신청 안내 보기 →'
    }
  ];

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', padding: 0 }}>
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '24px 20px 24px 20px' }}>
        
        {/* Back button */}
        <button onClick={onGoToRecommendedCrops} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: 24, display: 'flex' }}>
          <img src="/svg-assets/ui-icons/back.svg" alt="뒤로가기" style={{ width: 24, height: 24 }} />
        </button>

        {/* Title */}
        <div style={{ marginBottom: 32 }}>
          <h2 style={{ fontSize: '1.6rem', fontWeight: 900, color: '#191F28', marginBottom: 8, lineHeight: 1.35 }}>
            {districtName} 지역 농사 TIP
          </h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <img src="/svg-assets/ui-icons/check-circle.svg" alt="체크" style={{ width: 16, height: 16, filter: 'invert(52%) sepia(87%) saturate(347%) hue-rotate(94deg) brightness(91%) contrast(90%)' }} />
            <p style={{ fontSize: '0.85rem', color: '#6E7671', fontWeight: 600, margin: 0 }}>
              농사로 공식 자료를 바탕으로 정리했어요
            </p>
          </div>
        </div>

        {/* Premium Tip Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {tips.map((tip, idx) => (
            <motion.div 
              key={idx}
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1, duration: 0.4 }}
              style={{
                backgroundColor: '#F8FAF8',
                borderRadius: 20,
                padding: '20px',
                border: '1px solid #EAEFEA',
                boxShadow: '0 4px 20px rgba(0, 0, 0, 0.02)',
              }}
            >
              <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
                <div style={{ 
                  width: 44, height: 44, borderRadius: 14, backgroundColor: '#FFFFFF',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.04)', flexShrink: 0
                }}>
                  <img src={tip.icon} alt="아이콘" style={{ width: 24, height: 24 }} />
                </div>
                
                <div style={{ flex: 1, marginTop: 2 }}>
                  <h3 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#191F28', margin: '0 0 6px 0', lineHeight: 1.4 }}>
                    {tip.title}
                  </h3>
                  <p style={{
                    fontSize: '0.85rem', color: '#6E7671', fontWeight: 500,
                    lineHeight: 1.55, whiteSpace: 'pre-line', margin: '0 0 16px 0'
                  }}>
                    {tip.desc}
                  </p>

                  <button style={{
                    backgroundColor: '#FFFFFF', border: '1px solid #D1DFD7',
                    borderRadius: 10, padding: '7px 12px',
                    fontSize: '0.78rem', fontWeight: 700,
                    color: '#191F28', cursor: 'pointer',
                    display: 'inline-flex', alignItems: 'center'
                  }}>
                    {tip.link}
                  </button>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Fixed AI Chat Button (Positioned properly) */}
      <button className="floating-ai-btn" onClick={() => {}} title="AI Assistant" style={{ bottom: 100 }}>
        <img src="/svg-assets/ui-icons/ai-chat.svg" alt="AI 채팅" style={{ width: 26, height: 26, filter: 'brightness(0) invert(1)' }} />
      </button>

      {/* Fixed Bottom CTA Button */}
      <div style={{ padding: '16px 20px 32px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F0F2F5', position: 'relative', zIndex: 10 }}>
        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-farm-primary"
          onClick={onGoToCreateField}
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16 }}
        >
          마이 팜으로 이동
        </motion.button>
      </div>
    </div>
  );
};
