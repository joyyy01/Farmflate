import React from 'react';
import { motion } from 'framer-motion';

interface LandingViewProps {
  onLogin?: () => void;
}

export const LandingView: React.FC<LandingViewProps> = () => {
  const handleKakaoLogin = () => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/kakao';
  };

  return (
    <div className="full-screen-view" style={{
      justifyContent: 'space-between',
      padding: '28px 20px 32px 20px',
      backgroundColor: '#FFFFFF',
      background: 'radial-gradient(circle at 50% 0%, #E9F7EC 0%, #FFFFFF 65%)'
    }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', width: '100%' }}>
        
        {/* Brand Mascot & Logo Header */}
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.4 }}
          style={{ position: 'relative', marginBottom: 10 }}
        >
          {/* Sparkle decorative SVGs */}
          <img src="/svg-assets/ui-icons/star.svg" alt="" style={{ position: 'absolute', top: -6, left: -22, width: 16, height: 16, color: '#FFD95A' }} />
          <img src="/svg-assets/ui-icons/star.svg" alt="" style={{ position: 'absolute', top: 12, right: -24, width: 14, height: 14, color: '#2FA86A' }} />

          {/* Joy Mascot Character Avatar */}
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            backgroundColor: '#FFFFFF',
            border: '2.5px solid #2FA86A',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 8px 20px rgba(47, 168, 106, 0.2)',
            overflow: 'hidden'
          }}>
            <img
              src="/svg-assets/brand/mascot/joy.svg"
              alt="Farmflate 마스코트"
              style={{ width: 60, height: 60, objectFit: 'contain' }}
            />
          </div>
        </motion.div>

        {/* Pure Vector Brand Wordmark Logo */}
        <div style={{ marginBottom: 16 }}>
          <img
            src="/svg-assets/brand/wordmark.svg"
            alt="Farmflate"
            className="logo-wordmark"
            style={{ height: 34 }}
          />
        </div>

        {/* Main Headline */}
        <h1 style={{
          fontSize: '1.65rem',
          lineHeight: 1.42,
          fontWeight: 900,
          color: '#154f36',
          marginBottom: 8,
          margin: 0
        }}>
          내 땅에,<br />이 작물 심어도 될까요?
        </h1>

        {/* Subtitle */}
        <p style={{
          fontSize: '0.84rem',
          lineHeight: 1.65,
          color: '#6F7772',
          fontWeight: 500,
          marginBottom: 20,
          margin: '6px 0 20px 0'
        }}>
          지역과 작물을 선택하면 토양과 날씨를 분석해<br />재배 적합도와 위험 요소를 미리 알려드려요.
        </p>

        {/* Hero Card Container */}
        <motion.div
          initial={{ y: 12, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.15, duration: 0.5 }}
          style={{
            width: '100%',
            maxWidth: 360,
            aspectRatio: '360 / 230',
            backgroundColor: '#FFFFFF',
            borderRadius: 24,
            border: '1px solid #E1E8E4',
            boxShadow: '0 12px 32px rgba(29, 64, 44, 0.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 8,
            marginBottom: 24,
            overflow: 'hidden',
            flexShrink: 1
          }}
        >
          <img
            src="/assets/landing-hero.png"
            alt="농장과 분석 화면 일러스트"
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'contain'
            }}
          />
        </motion.div>

        {/* 3-Step Guided Process Chips */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          marginBottom: 8,
          width: '100%'
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 76 }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', backgroundColor: '#E9F7EC', border: '1px solid #A8DCC8', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 6, boxShadow: '0 3px 8px rgba(0,0,0,0.03)' }}>
              <img src="/svg-assets/ui-icons/location.svg" alt="지역 선택" style={{ width: 24, height: 24 }} />
            </div>
            <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#154F36' }}>지역 선택</span>
          </div>

          <span style={{ color: '#A8DCC8', fontSize: '1.3rem', fontWeight: 800, marginBottom: 18 }}>›</span>

          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 76 }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', backgroundColor: '#E9F7EC', border: '1px solid #A8DCC8', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 6, boxShadow: '0 3px 8px rgba(0,0,0,0.03)' }}>
              <img src="/svg-assets/crops/leaf.svg" alt="작물 선택" style={{ width: 24, height: 24 }} />
            </div>
            <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#154F36' }}>작물 선택</span>
          </div>

          <span style={{ color: '#A8DCC8', fontSize: '1.3rem', fontWeight: 800, marginBottom: 18 }}>›</span>

          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 76 }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', backgroundColor: '#E9F7EC', border: '1px solid #A8DCC8', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 6, boxShadow: '0 3px 8px rgba(0,0,0,0.03)' }}>
              <img src="/svg-assets/brand/mascot/guide.svg" alt="분석 & 안내" style={{ width: 30, height: 30 }} />
            </div>
            <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#154F36' }}>분석 &amp; 안내</span>
          </div>
        </div>

      </div>

      {/* Single Green Kakao Button Action */}
      <div style={{ width: '100%' }}>
        <motion.button
          whileTap={{ scale: 0.97 }}
          onClick={handleKakaoLogin}
          style={{
            width: '100%',
            height: 54,
            borderRadius: 29,
            background: 'linear-gradient(135deg, #2e9f5b, #38a766)',
            color: '#FFFFFF',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 10,
            fontSize: '1.02rem',
            fontWeight: 850,
            cursor: 'pointer',
            boxShadow: '0 9px 19px rgba(44, 151, 86, 0.22)'
          }}
        >
          <svg style={{ width: 22, height: 22, fill: '#FFFFFF' }} viewBox="0 0 24 24">
            <path d="M12 3c-4.97 0-9 3.185-9 7.115 0 2.557 1.707 4.8 4.27 6.09-.188.707-.68 2.564-.778 2.956-.123.493.18.487.38.354.157-.105 2.502-1.7 3.513-2.388.528.077 1.066.118 1.615.118 4.97 0 9-3.185 9-7.115S16.97 3 12 3z"/>
          </svg>
          카카오로 시작하기
        </motion.button>
      </div>
    </div>
  );
};
