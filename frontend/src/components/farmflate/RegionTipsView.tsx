import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, CheckCircle2, ChevronRight } from 'lucide-react';
import type { RegionReport } from '../../services/api';

interface RegionTipsViewProps {
  districtName: string;
  report?: RegionReport | null;
  onBack: () => void;
  onSave: () => void;
  onGoToCreateField: () => void;
  onOpenAIChat: () => void;
}

export const RegionTipsView: React.FC<RegionTipsViewProps> = ({
  districtName,
  report,
  onBack,
  onSave,
  onGoToCreateField,
  onOpenAIChat: _onOpenAIChat
}) => {
  // Extract short district name (e.g. "전주시" from "전북특별자치도 전주시")
  const shortDistrict = districtName.split(' ').pop() || districtName;

  const getTipMeta = (title: string, tipCode?: string) => {
    if (title.includes('토양') || tipCode?.includes('SOIL')) {
      return {
        icon: '/svg-assets/crops/soil-sprout.svg',
        category: '토양 검정',
        badgeBg: '#E0F2FE',
        badgeColor: '#0284C7'
      };
    }
    if (title.includes('고온') || title.includes('폭염') || tipCode?.includes('HEAT')) {
      return {
        icon: '/svg-assets/weather/sunny.svg',
        category: '폭염 대비',
        badgeBg: '#FFF4DC',
        badgeColor: '#FF842F'
      };
    }
    if (title.includes('재해') || title.includes('경보') || tipCode?.includes('HAZARD')) {
      return {
        icon: '/svg-assets/ui-icons/warning-triangle.svg',
        category: '재해 대비',
        badgeBg: '#FFEBE0',
        badgeColor: '#FF4D4F'
      };
    }
    return {
      icon: '/svg-assets/weather/rain.svg',
      category: '배수 관리',
      badgeBg: '#E9F7EC',
      badgeColor: '#2FA86A'
    };
  };

  const rawTips = report?.tips && report.tips.length > 0 ? report.tips : [
    {
      tipCode: 'DRAINAGE_BEFORE_RAIN',
      title: '장마철 배수 관리가 중요해요',
      summary: '이 지역은 여름철 많은 비가 몰릴 수 있어 밭 주변 배수로 점검이 필요해요.',
      sourceName: '농사로 공식자료',
      sourceUrl: 'https://www.nongsaro.go.kr'
    },
    {
      tipCode: 'SOIL_TEST_GUIDE',
      title: '시군구 농업기술센터 토양검정 활용',
      summary: '무료 토양 검정 서비스를 통해 정확한 pH와 비료 처방전을 받아보세요.',
      sourceName: '농촌진흥청 흙토람',
      sourceUrl: 'https://soil.rda.go.kr'
    }
  ];

  const dynamicTips = rawTips.map(t => {
    const meta = getTipMeta(t.title, t.tipCode);
    return {
      title: t.title,
      desc: t.summary,
      linkText: `${t.sourceName || '공식 자료'} 보기`,
      sourceUrl: t.sourceUrl || 'https://www.nongsaro.go.kr',
      icon: meta.icon,
      category: meta.category,
      badgeBg: meta.badgeBg,
      badgeColor: meta.badgeColor
    };
  });

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', padding: 0 }}>
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '24px 20px 24px 20px' }}>
        
        {/* Back button */}
        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: 24, display: 'flex' }}>
          <ArrowLeft size={24} color="#191F28" />
        </button>

        {/* Header Title */}
        <div style={{ marginBottom: 32 }}>
          <h2 style={{ fontSize: '1.55rem', fontWeight: 900, color: '#191F28', marginBottom: 8, lineHeight: 1.35, letterSpacing: '-0.02em' }}>
            {shortDistrict} 지역 농사 TIP
          </h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <CheckCircle2 size={16} color="#2FA86A" />
            <p style={{ fontSize: '0.84rem', color: '#6E7671', fontWeight: 600, margin: 0 }}>
              농사로 공식 자료를 바탕으로 정리했어요
            </p>
          </div>
        </div>

        {/* Premium Diversified Tip Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {dynamicTips.map((tip, idx) => (
            <motion.div 
              key={idx}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1, duration: 0.35 }}
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
              {/* Clean Category Badge Pill on Top Right */}
              <div style={{
                position: 'absolute', top: 16, right: 16,
                backgroundColor: tip.badgeBg, color: tip.badgeColor,
                borderRadius: 12, padding: '4px 10px',
                fontSize: '0.74rem', fontWeight: 800
              }}>
                {tip.category}
              </div>

              <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
                <div style={{ 
                  width: 46, height: 46, borderRadius: 14, backgroundColor: '#FFFFFF',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.04)', flexShrink: 0
                }}>
                  <img src={tip.icon} alt="아이콘" style={{ width: 26, height: 26, objectFit: 'contain' }} />
                </div>
                
                <div style={{ flex: 1, marginTop: 2 }}>
                  <h3 style={{ fontSize: '1.08rem', fontWeight: 850, color: '#191F28', margin: '0 0 6px 0', paddingRight: 75, lineHeight: 1.4 }}>
                    {tip.title}
                  </h3>
                  <p style={{
                    fontSize: '0.86rem', color: '#6E7671', fontWeight: 500,
                    lineHeight: 1.55, whiteSpace: 'pre-line', margin: '0 0 16px 0'
                  }}>
                    {tip.desc}
                  </p>

                  <button
                    onClick={() => window.open(tip.sourceUrl, '_blank')}
                    style={{
                      backgroundColor: '#FFFFFF', border: '1px solid #D1DFD7',
                      borderRadius: 10, padding: '8px 14px',
                      fontSize: '0.8rem', fontWeight: 750,
                      color: '#191F28', cursor: 'pointer',
                      display: 'inline-flex', alignItems: 'center', gap: 4,
                      boxShadow: '0 2px 6px rgba(0,0,0,0.02)'
                    }}
                  >
                    {tip.linkText} <ChevronRight size={14} color="#2FA86A" />
                  </button>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Fixed Bottom CTA Buttons */}
      <div style={{ padding: '16px 20px 32px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F0F2F5', position: 'relative', zIndex: 10, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-farm-primary"
          onClick={onSave}
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16 }}
        >
          저장
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-farm-primary"
          onClick={onGoToCreateField}
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16, backgroundColor: '#EEF8F1', color: '#2FA86A' }}
        >
          마이 팜으로 이동
        </motion.button>
      </div>
    </div>
  );
};
