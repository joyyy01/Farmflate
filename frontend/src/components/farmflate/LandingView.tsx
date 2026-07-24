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
      padding: '36px 20px 32px 20px',
      backgroundColor: '#FFFFFF',
      background: 'radial-gradient(circle at 50% 10%, #E9F7EC 0%, #FFFFFF 75%)'
    }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', width: '100%' }}>
        
        {/* Brand Mascot & Logo Header */}
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.4 }}
          style={{ position: 'relative', marginBottom: 12 }}
        >
          {/* Sparkle decorative SVGs */}
          <img src="/svg-assets/ui-icons/star.svg" alt="" style={{ position: 'absolute', top: -6, left: -22, width: 16, height: 16 }} />
          <img src="/svg-assets/ui-icons/star.svg" alt="" style={{ position: 'absolute', top: 12, right: -24, width: 14, height: 14 }} />

          {/* Joy Mascot Character Avatar */}
          <div style={{
            width: 76, height: 76, borderRadius: '50%',
            backgroundColor: '#FFFFFF',
            border: '2.5px solid #2FA86A',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            overflow: 'hidden'
          }}>
            <img
              src="/svg-assets/brand/mascot/joy.svg"
              alt="Farmflate 마스코트"
              style={{ width: 64, height: 64, objectFit: 'contain' }}
            />
          </div>
        </motion.div>

        {/* Pure Vector Brand Wordmark Logo */}
        <div style={{ marginBottom: 14 }}>
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
          lineHeight: 1.4,
          fontWeight: 900,
          color: '#154f36',
          marginBottom: 6,
          margin: 0,
          letterSpacing: '-0.03em'
        }}>
          내 땅에,<br />이 작물 심어도 될까요?
        </h1>

        {/* Subtitle */}
        <p style={{
          fontSize: '0.84rem',
          lineHeight: 1.6,
          color: '#6F7772',
          fontWeight: 500,
          marginBottom: 20,
          margin: '6px 0 20px 0',
          letterSpacing: '-0.01em'
        }}>
          지역과 작물을 선택하면 토양과 날씨를 분석해<br />재배 적합도와 위험 요소를 미리 알려드려요.
        </p>

        {/* Original Hero Illustration (Borderless & Blended) */}
        <motion.div
          initial={{ y: 12, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.15, duration: 0.5 }}
          style={{
            width: '100%',
            maxWidth: 380,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 20,
            overflow: 'hidden'
          }}
        >
          <img
            src="/assets/landing-hero.png"
            alt="농장과 분석 화면 일러스트"
            style={{
              width: '100%',
              height: 'auto',
              maxHeight: 220,
              objectFit: 'contain',
              borderRadius: 20
            }}
          />
        </motion.div>

        {/* 3-Step Guided Process Chips */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          marginBottom: 10,
          width: '100%'
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 76 }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', backgroundColor: '#E9F7EC', border: '1px solid #A8DCC8', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 6 }}>
              <img src="/svg-assets/ui-icons/location.svg" alt="지역 선택" style={{ width: 24, height: 24 }} />
            </div>
            <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#154F36' }}>지역 선택</span>
          </div>

          <span style={{ color: '#A8DCC8', fontSize: '1.3rem', fontWeight: 800, marginBottom: 18 }}>›</span>

          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 76 }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', backgroundColor: '#E9F7EC', border: '1px solid #A8DCC8', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 6 }}>
              <img src="/svg-assets/crops/leaf.svg" alt="작물 선택" style={{ width: 24, height: 24 }} />
            </div>
            <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#154F36' }}>작물 선택</span>
          </div>

          <span style={{ color: '#A8DCC8', fontSize: '1.3rem', fontWeight: 800, marginBottom: 18 }}>›</span>

          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 76 }}>
            <div style={{ width: 48, height: 48, borderRadius: '50%', backgroundColor: '#E9F7EC', border: '1px solid #A8DCC8', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 6 }}>
              <img src="/svg-assets/brand/mascot/guide.svg" alt="분석 & 안내" style={{ width: 30, height: 30 }} />
            </div>
            <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#154F36' }}>분석 &amp; 안내</span>
          </div>
        </div>

      </div>

      {/* Official Kakao Brand Yellow (#FEE500) Button */}
      <div style={{ width: '100%' }}>
        <motion.button
          whileTap={{ scale: 0.97 }}
          onClick={handleKakaoLogin}
          style={{
            width: '100%',
            height: 54,
            borderRadius: 27,
            backgroundColor: '#FEE500',
            color: '#191F28',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 10,
            fontSize: '1.02rem',
            fontWeight: 850,
            cursor: 'pointer'
          }}
        >
          <svg style={{ width: 22, height: 22, fill: '#191F28' }} viewBox="0 0 24 24">
            <path d="M12 3c-4.97 0-9 3.185-9 7.115 0 2.557 1.707 4.8 4.27 6.09-.188.707-.68 2.564-.778 2.956-.123.493.18.487.38.354.157-.105 2.502-1.7 3.513-2.388.528.077 1.066.118 1.615.118 4.97 0 9-3.185 9-7.115S16.97 3 12 3z"/>
          </svg>
          카카오로 시작하기
        </motion.button>
      </div>
    </div>
  );
};
