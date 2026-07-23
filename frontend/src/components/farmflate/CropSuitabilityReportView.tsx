import React from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, Share2, AlertTriangle } from 'lucide-react';

interface CropSuitabilityReportViewProps {
  cropName?: string;
  score?: number;
  onBack: () => void;
  onRegisterCrop: () => void;
}

export const CropSuitabilityReportView: React.FC<CropSuitabilityReportViewProps> = ({
  cropName = '감자',
  score = 86,
  onBack,
  onRegisterCrop
}) => {
  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', padding: '0 20px 96px 20px', justifyContent: 'space-between' }}>
      <div>
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

        {/* Subtitle */}
        <div style={{ fontSize: '0.9rem', fontWeight: 850, color: '#25804b', marginBottom: 18 }}>
          우리집 텃밭 · {cropName}
        </div>

        {/* Donut Ring & Score & Pure Vector SVG Potato Mascot */}
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
              <span style={{ fontSize: '3.2rem', fontWeight: 900, color: '#145b39', lineHeight: 1 }}>{score}</span>
              <small style={{ color: '#848985', fontSize: '0.86rem', fontWeight: 500, marginTop: 2 }}>/100</small>
            </div>

            {/* Pure Vector SVG Potato Mascot */}
            <img
              src="/svg-assets/crops/potato.svg"
              alt="감자 캐릭터"
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

          <div style={{ fontSize: '0.9rem', fontWeight: 850, color: '#202a24', marginTop: 8 }}>
            ✨ 현재 재배를 시작하기 좋은 환경입니다
          </div>
        </div>

        {/* 4 Environment Status Cards with Pure Vector SVG Category Icons */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          <div style={{ border: '1px solid #e1e5e2', borderRadius: 12, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/climate.svg" alt="기후" style={{ width: 39, height: 39, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.7rem', color: '#747a76' }}>기후 적합도</div>
              <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#309159', marginTop: 2 }}>양호</div>
            </div>
          </div>

          <div style={{ border: '1px solid #e1e5e2', borderRadius: 12, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/soil.svg" alt="토양" style={{ width: 39, height: 39, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.7rem', color: '#747a76' }}>토양 적합도</div>
              <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#309159', marginTop: 2 }}>적정</div>
            </div>
          </div>

          <div style={{ border: '1px solid #e1e5e2', borderRadius: 12, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/greenhouse.svg" alt="환경" style={{ width: 39, height: 39, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.7rem', color: '#747a76' }}>재배 환경</div>
              <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#309159', marginTop: 2 }}>양호</div>
            </div>
          </div>

          <div style={{ border: '1px solid #ffd9b7', borderRadius: 12, padding: '12px 14px', background: 'linear-gradient(135deg, #fffdfa, #fff8f0)', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/warning.svg" alt="위험도" style={{ width: 39, height: 39, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.7rem', color: '#747a76' }}>위험도</div>
              <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#ff7d32', marginTop: 2 }}>주의</div>
            </div>
          </div>
        </div>

        {/* Core Risks Section */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <span style={{ fontSize: '0.86rem', fontWeight: 850, color: '#202a24' }}>핵심 위험</span>
            <span style={{ fontSize: '0.74rem', fontWeight: 800, color: '#3a9559', cursor: 'pointer' }}>상세 보기 ›</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ backgroundColor: '#f6f7f6', borderRadius: 10, padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <AlertTriangle size={16} color="#ff892f" style={{ flexShrink: 0 }} />
              <div>
                <strong style={{ display: 'block', fontSize: '0.78rem', color: '#202a24' }}>폭염 가능성</strong>
                <span style={{ fontSize: '0.68rem', color: '#818783' }}>여름철 고온에 초기 생육이 늦어질 수 있어요.</span>
              </div>
            </div>

            <div style={{ backgroundColor: '#f6f7f6', borderRadius: 10, padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <AlertTriangle size={16} color="#ff892f" style={{ flexShrink: 0 }} />
              <div>
                <strong style={{ display: 'block', fontSize: '0.78rem', color: '#202a24' }}>집중호우</strong>
                <span style={{ fontSize: '0.68rem', color: '#818783' }}>배수가 나쁘면 씨감자가 썩을 수 있어요.</span>
              </div>
            </div>

            <div style={{ backgroundColor: '#f6f7f6', borderRadius: 10, padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <AlertTriangle size={16} color="#ff892f" style={{ flexShrink: 0 }} />
              <div>
                <strong style={{ display: 'block', fontSize: '0.78rem', color: '#202a24' }}>강풍</strong>
                <span style={{ fontSize: '0.68rem', color: '#818783' }}>멀칭 비닐이 손상되지 않게 고정이 필요해요.</span>
              </div>
            </div>
          </div>
        </div>

        {/* Preparation Steps with Pure Vector SVG Prep Icons */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: '0.86rem', fontWeight: 850, color: '#202a24', marginBottom: 10 }}>
            시작 전 준비사항
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
            <div style={{ border: '1px solid #e1e5e2', borderRadius: 11, backgroundColor: '#FFFFFF', textAlign: 'center', padding: '8px 4px', fontSize: '0.68rem', color: '#5e6560' }}>
              <img src="/svg-assets/report/prep/seed-potato.svg" alt="씨감자" style={{ width: 28, height: 28, objectFit: 'contain', margin: '0 auto 4px' }} />
              씨감자 구매
            </div>

            <div style={{ border: '1px solid #e1e5e2', borderRadius: 11, backgroundColor: '#FFFFFF', textAlign: 'center', padding: '8px 4px', fontSize: '0.68rem', color: '#5e6560' }}>
              <img src="/svg-assets/report/prep/field-preparation.svg" alt="밭 정비" style={{ width: 28, height: 28, objectFit: 'contain', margin: '0 auto 4px' }} />
              밭 정비
            </div>

            <div style={{ border: '1px solid #e1e5e2', borderRadius: 11, backgroundColor: '#FFFFFF', textAlign: 'center', padding: '8px 4px', fontSize: '0.68rem', color: '#5e6560' }}>
              <img src="/svg-assets/report/prep/fertilizer.svg" alt="밑거름" style={{ width: 28, height: 28, objectFit: 'contain', margin: '0 auto 4px' }} />
              밑거름 준비
            </div>

            <div style={{ border: '1px solid #e1e5e2', borderRadius: 11, backgroundColor: '#FFFFFF', textAlign: 'center', padding: '8px 4px', fontSize: '0.68rem', color: '#5e6560' }}>
              <img src="/svg-assets/report/prep/water-check.svg" alt="물 빠짐" style={{ width: 28, height: 28, objectFit: 'contain', margin: '0 auto 4px' }} />
              물 빠짐 확인
            </div>
          </div>
        </div>

        {/* Date banner */}
        <div style={{
          borderRadius: 8,
          background: 'linear-gradient(90deg, #e7f6e9, #eef9ef)',
          padding: '8px 12px',
          textAlign: 'center',
          fontSize: '0.7rem',
          color: '#84908a',
          marginBottom: 20
        }}>
          분석 기준일: 2026년 7월 23일
        </div>
      </div>

      {/* CTA Button */}
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
          boxShadow: '0 8px 18px rgba(47, 154, 88, 0.16)'
        }}
      >
        농작물 등록하기
      </motion.button>
    </div>
  );
};
