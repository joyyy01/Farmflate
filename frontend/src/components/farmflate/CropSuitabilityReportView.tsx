import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, AlertTriangle } from 'lucide-react';

interface CropSuitabilityReportViewProps {
  fieldName?: string;
  cropName?: string;
  score?: number;
  onBack: () => void;
  onRegisterCrop: () => void;
}

export const CropSuitabilityReportView: React.FC<CropSuitabilityReportViewProps> = ({
  fieldName = '우리집 텃밭',
  cropName = '감자',
  score = 86,
  onBack,
  onRegisterCrop
}) => {
  // SVG Ring Gauge Calculations
  const radius = 64;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (circumference * Math.min(score, 100)) / 100;

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
          marginBottom: 20
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
        <div style={{ fontSize: '0.94rem', fontWeight: 850, color: '#154F36', marginBottom: 20 }}>
          {fieldName} · {cropName}
        </div>

        {/* Circular Ring Gauge & Score */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: 24 }}>
          <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 600, marginBottom: 12 }}>
            농작물 적합도 점수
          </span>

          <div style={{ position: 'relative', width: 160, height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 14 }}>
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
                {score}
              </span>
            </div>
          </div>

          <h2 style={{ fontSize: '1.08rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center' }}>
            현재 재배를 시작하기 좋은 환경입니다
          </h2>
        </div>

        {/* 4 Environment Status Cards (2x2 Grid) */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          <div style={{ border: '1px solid #EAEFEA', borderRadius: 16, padding: '16px 14px', backgroundColor: '#FFFFFF' }}>
            <div style={{ fontSize: '1.2rem', marginBottom: 4 }}>🌦️</div>
            <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>기후 적합도</div>
            <div style={{ fontSize: '0.96rem', fontWeight: 850, color: '#191F28', marginTop: 2 }}>양호</div>
          </div>

          <div style={{ border: '1px solid #EAEFEA', borderRadius: 16, padding: '16px 14px', backgroundColor: '#FFFFFF' }}>
            <div style={{ fontSize: '1.2rem', marginBottom: 4 }}>🌱</div>
            <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>토양 적합도</div>
            <div style={{ fontSize: '0.96rem', fontWeight: 850, color: '#191F28', marginTop: 2 }}>적정</div>
          </div>

          <div style={{ border: '1px solid #EAEFEA', borderRadius: 16, padding: '16px 14px', backgroundColor: '#FFFFFF' }}>
            <div style={{ fontSize: '1.2rem', marginBottom: 4 }}>🏡</div>
            <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>재배 환경</div>
            <div style={{ fontSize: '0.96rem', fontWeight: 850, color: '#191F28', marginTop: 2 }}>양호</div>
          </div>

          <div style={{ border: '1px solid #FFE0D0', borderRadius: 16, padding: '16px 14px', backgroundColor: '#FFFFFF' }}>
            <div style={{ fontSize: '1.2rem', marginBottom: 4 }}>⚠️</div>
            <div style={{ fontSize: '0.74rem', color: '#6E7671', fontWeight: 600 }}>위험도</div>
            <div style={{ fontSize: '0.96rem', fontWeight: 850, color: '#FF7F2B', marginTop: 2 }}>주의</div>
          </div>
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
            현재 평균기온은 {cropName}가 잘 자라는 범위입니다. 토양 산도도 적절합니다. 다만 최근 강수량이 적어 초기 물관리에 주의가 필요합니다.
          </div>
        </div>

        {/* 핵심 위험 List */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            핵심 위험
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <AlertTriangle size={18} color="#FF7F2B" style={{ flexShrink: 0, marginTop: 2 }} />
              <div>
                <strong style={{ fontSize: '0.9rem', color: '#191F28', fontWeight: 850, display: 'block', marginBottom: 2 }}>
                  폭염 가능성
                </strong>
                <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>
                  여름철 고온에 초기 생육이 늦어질 수 있어요
                </span>
              </div>
            </div>

            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <AlertTriangle size={18} color="#FF7F2B" style={{ flexShrink: 0, marginTop: 2 }} />
              <div>
                <strong style={{ fontSize: '0.9rem', color: '#191F28', fontWeight: 850, display: 'block', marginBottom: 2 }}>
                  집중호우
                </strong>
                <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>
                  배수가 나쁘면 씨{cropName}가 썩을 수 있어요
                </span>
              </div>
            </div>

            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <AlertTriangle size={18} color="#FF7F2B" style={{ flexShrink: 0, marginTop: 2 }} />
              <div>
                <strong style={{ fontSize: '0.9rem', color: '#191F28', fontWeight: 850, display: 'block', marginBottom: 2 }}>
                  강풍
                </strong>
                <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>
                  멀칭 비닐이 손상되지 않게 고정이 필요해요
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 시작 전 준비사항 */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            시작 전 준비사항
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.86rem', fontWeight: 750, color: '#191F28' }}>
              씨{cropName} 구매
            </div>
            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.86rem', fontWeight: 750, color: '#191F28' }}>
              멀칭 준비
            </div>
            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.86rem', fontWeight: 750, color: '#191F28' }}>
              밭갈이
            </div>
            <div style={{ backgroundColor: '#FFFFFF', borderRadius: 14, padding: '14px 16px', border: '1px solid #EAEFEA', fontSize: '0.86rem', fontWeight: 750, color: '#191F28' }}>
              배수로 확인
            </div>
          </div>
        </div>

        {/* 현재 관리 포인트 (Green Box) */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 850, color: '#191F28', marginBottom: 10 }}>
            현재 관리 포인트
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
              현재는 심기 전 단계예요.
            </strong>
            지금은 심기 준비를 마치는 게 가장 중요해요. 심은 후부터 물주기·웃거름·병해충 관리 리포트가 제공돼요.
          </div>
        </div>

        {/* Date Footer */}
        <div style={{ textAlign: 'right', fontSize: '0.74rem', color: '#8B95A1', fontWeight: 600, marginBottom: 20 }}>
          분석 기준일: {new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })}
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
