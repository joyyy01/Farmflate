import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, MapPin, Calendar } from 'lucide-react';
import { DuotoneIcon } from '../common/DuotoneIcon';

interface CropConditionInputViewProps {
  onBack: () => void;
  onStartAnalysis: (cropName: string, stage: string, mode: string) => void;
  onOpenExplore: () => void;
  selectedRegionName: string;
}

export const CropConditionInputView: React.FC<CropConditionInputViewProps> = ({
  onBack,
  onStartAnalysis,
  onOpenExplore,
  selectedRegionName
}) => {
  const [fieldName, setFieldName] = useState('');
  const [selectedCrop, setSelectedCrop] = useState('감자');
  const [stage, setStage] = useState<'before' | 'growing'>('before');
  const [farmType, setFarmType] = useState<'outdoor' | 'indoor'>('outdoor');
  const [rawDateIso, setRawDateIso] = useState(new Date().toISOString().substring(0, 10));
  const [startDate, setStartDate] = useState(
    new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
  );

  /* Available Crops */
  const crops = [
    { name: '사과', icon: 'apple' },
    { name: '배', icon: 'pear' },
    { name: '감자', icon: 'potato' },
    { name: '오이', icon: 'cucumber' },
    { name: '상추', icon: 'lettuce' }
  ];

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', position: 'relative', overflow: 'hidden' }}>
      
      {/* Scrollable Main Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '0 20px 100px 20px' }}>
        
        {/* Minimal Header Bar */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '32px 1fr 32px',
          alignItems: 'center',
          height: 60,
          borderBottom: '1px solid #F0F2F1',
          marginBottom: 20
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#191F28', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center' }}>
            농작물 등록
          </h1>
          <div />
        </div>

        {/* Clean Region Card Tile (Sufficient Space Between Text & Link) */}
        <div style={{
          backgroundColor: '#F8FAF8',
          borderRadius: 16,
          padding: '14px 16px',
          border: '1px solid #EAEFEA',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
          boxSizing: 'border-box'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <MapPin size={18} color="#2FA86A" />
            <span style={{ fontSize: '0.92rem', fontWeight: 850, color: '#191F28' }}>
              {selectedRegionName}
            </span>
          </div>

          <button
            type="button"
            onClick={onOpenExplore}
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #D1DFD7',
              borderRadius: 10,
              padding: '6px 12px',
              fontSize: '0.78rem',
              fontWeight: 800,
              color: '#2FA86A',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              flexShrink: 0
            }}
          >
            지역 변경 ›
          </button>
        </div>

        {/* 1. 밭 이름 (Field Name Input) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 8, display: 'block' }}>
            밭 이름
          </label>
          <input
            type="text"
            placeholder="예: 우리집 텃밭"
            value={fieldName}
            onChange={e => setFieldName(e.target.value)}
            style={{
              width: '100%',
              height: 50,
              padding: '0 16px',
              border: '1px solid #EAEFEA',
              borderRadius: 14,
              fontSize: '0.9rem',
              fontWeight: 650,
              color: '#191F28',
              outline: 'none',
              backgroundColor: '#F8FAF8',
              boxSizing: 'border-box',
              transition: 'all 0.15s ease'
            }}
            onFocus={(e) => {
              e.target.style.borderColor = '#2FA86A';
              e.target.style.backgroundColor = '#FFFFFF';
            }}
            onBlur={(e) => {
              e.target.style.borderColor = '#EAEFEA';
              e.target.style.backgroundColor = '#F8FAF8';
            }}
          />
        </div>

        {/* 2. 작물 선택 (Sleek Grid Tiles) */}
        <div style={{ marginBottom: 26 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 12, display: 'block' }}>
            작물 선택
          </label>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(5, 1fr)',
            gap: 8,
            width: '100%'
          }}>
            {crops.map(crop => {
              const isSelected = selectedCrop === crop.name;
              return (
                <motion.div
                  key={crop.name}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => setSelectedCrop(crop.name)}
                  style={{
                    backgroundColor: isSelected ? '#F2FAF4' : '#FFFFFF',
                    border: isSelected ? '2px solid #2FA86A' : '1px solid #EAEFEA',
                    borderRadius: 16,
                    padding: '14px 4px 12px 4px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    boxShadow: isSelected ? '0 4px 12px rgba(47, 168, 106, 0.12)' : 'none',
                    transition: 'all 0.15s ease'
                  }}
                >
                  <DuotoneIcon type={crop.icon} bgSize={40} iconSize={30} />

                  <span style={{
                    fontSize: '0.82rem',
                    fontWeight: isSelected ? 850 : 650,
                    color: isSelected ? '#154F36' : '#4B5563',
                    marginTop: 6
                  }}>
                    {crop.name}
                  </span>
                </motion.div>
              );
            })}
          </div>
        </div>

        {/* 3. 현재 단계 (Modern Segmented Toggle Track) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 10, display: 'block' }}>
            현재 단계
          </label>

          <div style={{
            display: 'flex',
            backgroundColor: '#F3F4F6',
            borderRadius: 14,
            padding: 4,
            gap: 4
          }}>
            <button
              type="button"
              onClick={() => setStage('before')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 11,
                border: 'none',
                backgroundColor: stage === 'before' ? '#FFFFFF' : 'transparent',
                color: stage === 'before' ? '#154F36' : '#6B7280',
                fontWeight: stage === 'before' ? 850 : 600,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: stage === 'before' ? '0 2px 6px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease'
              }}
            >
              심기 전
            </button>

            <button
              type="button"
              onClick={() => setStage('growing')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 11,
                border: 'none',
                backgroundColor: stage === 'growing' ? '#FFFFFF' : 'transparent',
                color: stage === 'growing' ? '#154F36' : '#6B7280',
                fontWeight: stage === 'growing' ? 850 : 600,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: stage === 'growing' ? '0 2px 6px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease'
              }}
            >
              이미 재배 중
            </button>
          </div>
        </div>

        {/* 4. 재배 방식 (Modern Segmented Toggle Track) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 방식
          </label>

          <div style={{
            display: 'flex',
            backgroundColor: '#F3F4F6',
            borderRadius: 14,
            padding: 4,
            gap: 4
          }}>
            <button
              type="button"
              onClick={() => setFarmType('outdoor')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 11,
                border: 'none',
                backgroundColor: farmType === 'outdoor' ? '#FFFFFF' : 'transparent',
                color: farmType === 'outdoor' ? '#154F36' : '#6B7280',
                fontWeight: farmType === 'outdoor' ? 850 : 600,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: farmType === 'outdoor' ? '0 2px 6px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease'
              }}
            >
              노지
            </button>

            <button
              type="button"
              onClick={() => setFarmType('indoor')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 11,
                border: 'none',
                backgroundColor: farmType === 'indoor' ? '#FFFFFF' : 'transparent',
                color: farmType === 'indoor' ? '#154F36' : '#6B7280',
                fontWeight: farmType === 'indoor' ? 850 : 600,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: farmType === 'indoor' ? '0 2px 6px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease'
              }}
            >
              시설
            </button>
          </div>
        </div>

        {/* 5. 재배 시작일 (Clean Single-Date Row with Invisible Input Trigger) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 시작일
          </label>

          <div style={{
            backgroundColor: '#F8FAF8',
            borderRadius: 14,
            padding: '14px 16px',
            border: '1px solid #EAEFEA',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            position: 'relative'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Calendar size={18} color="#2FA86A" />
              <span style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28' }}>
                {startDate}
              </span>
            </div>

            <div style={{ position: 'relative', cursor: 'pointer' }}>
              <span style={{
                backgroundColor: '#FFFFFF',
                border: '1px solid #D1DFD7',
                borderRadius: 8,
                padding: '4px 10px',
                fontSize: '0.78rem',
                fontWeight: 800,
                color: '#2FA86A'
              }}>
                변경
              </span>
              <input
                type="date"
                value={rawDateIso}
                onChange={(e) => {
                  if (e.target.value) {
                    setRawDateIso(e.target.value);
                    const parts = e.target.value.split('-');
                    setStartDate(`${parts[0]}년 ${parseInt(parts[1])}월 ${parseInt(parts[2])}일`);
                  }
                }}
                style={{
                  position: 'absolute',
                  inset: 0,
                  opacity: 0,
                  width: '100%',
                  height: '100%',
                  cursor: 'pointer'
                }}
              />
            </div>
          </div>
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
          onClick={() => onStartAnalysis(selectedCrop, stage, farmType)}
          style={{
            width: '100%',
            height: 56,
            fontSize: '1.05rem',
            borderRadius: 16
          }}
        >
          적합도 분석하기
        </motion.button>
      </div>
    </div>
  );
};
