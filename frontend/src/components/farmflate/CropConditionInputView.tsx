import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, MapPin, Calendar, Check, Tag } from 'lucide-react';
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
  const [startDate, setStartDate] = useState(
    new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
  );

  /* Available Crops with Vector Icons & Descriptions */
  const crops = [
    { name: '사과', icon: 'apple', desc: '과수 작물' },
    { name: '배', icon: 'pear', desc: '과수 작물' },
    { name: '감자', icon: 'potato', desc: '구근 작물' },
    { name: '오이', icon: 'cucumber', desc: '과채 작물' },
    { name: '상추', icon: 'lettuce', desc: '엽채 작물' }
  ];

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', position: 'relative', overflow: 'hidden' }}>
      
      {/* Scrollable Main Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '0 20px 100px 20px' }}>
        
        {/* Header Bar */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '32px 1fr 32px',
          alignItems: 'center',
          height: 60,
          borderBottom: '1px solid #F0F2F1',
          marginBottom: 16
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#191F28', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center' }}>
            농작물 등록
          </h1>
          <div />
        </div>

        {/* Region Location Pill Badge */}
        <div style={{
          backgroundColor: '#F8FAF8',
          borderRadius: 16,
          padding: '12px 16px',
          border: '1px solid #EAEFEA',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 28, height: 28, borderRadius: '50%',
              backgroundColor: '#E9F7EC', color: '#2FA86A',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <MapPin size={16} color="#2FA86A" />
            </div>
            <div>
              <div style={{ fontSize: '0.72rem', color: '#6E7671', fontWeight: 600 }}>분석 대상 지역</div>
              <div style={{ fontSize: '0.92rem', fontWeight: 850, color: '#191F28' }}>{selectedRegionName}</div>
            </div>
          </div>
          <button
            onClick={onOpenExplore}
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #D1DFD7',
              borderRadius: 10,
              padding: '6px 12px',
              fontSize: '0.78rem',
              fontWeight: 750,
              color: '#2FA86A',
              cursor: 'pointer'
            }}
          >
            지역 변경
          </button>
        </div>

        {/* 1. 밭 이름 (Field Name) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
            <Tag size={16} color="#2FA86A" /> 밭 이름
          </label>
          <input
            type="text"
            placeholder="예: 우리집 텃밭, 고창 감자 1밭"
            value={fieldName}
            onChange={e => setFieldName(e.target.value)}
            style={{
              width: '100%',
              height: 50,
              padding: '0 16px',
              border: '1.5px solid #EAEFEA',
              borderRadius: 14,
              fontSize: '0.9rem',
              fontWeight: 650,
              color: '#191F28',
              outline: 'none',
              backgroundColor: '#F8FAF8',
              boxSizing: 'border-box',
              transition: 'all 0.2s ease'
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

        {/* 2. 작물 선택 (Balanced Tile Grid Cards) */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <label style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28', margin: 0 }}>
              작물 선택
            </label>
            <span style={{ fontSize: '0.78rem', color: '#2FA86A', fontWeight: 800 }}>
              선택: {selectedCrop}
            </span>
          </div>

          {/* Equal Width 5-Card Uniform Grid */}
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
                    backgroundColor: isSelected ? '#E9F7EC' : '#F8FAF8',
                    border: isSelected ? '2px solid #2FA86A' : '1px solid #EAEFEA',
                    borderRadius: 16,
                    padding: '12px 4px 10px 4px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    position: 'relative',
                    boxShadow: isSelected ? '0 4px 12px rgba(47, 168, 106, 0.15)' : 'none',
                    transition: 'all 0.2s cubic-bezier(0.16, 1, 0.3, 1)'
                  }}
                >
                  {/* Selected Badge Check Mark */}
                  {isSelected && (
                    <div style={{
                      position: 'absolute', top: -5, right: -5,
                      width: 18, height: 18, borderRadius: '50%',
                      backgroundColor: '#2FA86A', color: '#FFFFFF',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.15)'
                    }}>
                      <Check size={11} strokeWidth={3} />
                    </div>
                  )}

                  <DuotoneIcon type={crop.icon} bgSize={40} iconSize={30} />

                  <span style={{
                    fontSize: '0.82rem',
                    fontWeight: isSelected ? 850 : 650,
                    color: isSelected ? '#154F36' : '#333D4B',
                    marginTop: 6
                  }}>
                    {crop.name}
                  </span>
                </motion.div>
              );
            })}
          </div>
        </div>

        {/* 3. 현재 단계 (Modern Segmented Pill Toggle) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28', marginBottom: 10, display: 'block' }}>
            현재 단계
          </label>

          <div style={{
            display: 'flex',
            backgroundColor: '#F1F5F3',
            borderRadius: 16,
            padding: 4,
            gap: 4
          }}>
            <button
              type="button"
              onClick={() => setStage('before')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 12,
                border: 'none',
                backgroundColor: stage === 'before' ? '#FFFFFF' : 'transparent',
                color: stage === 'before' ? '#154F36' : '#6E7671',
                fontWeight: stage === 'before' ? 850 : 650,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: stage === 'before' ? '0 2px 8px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.2s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6
              }}
            >
              <span>🌱</span> 심기 전
            </button>

            <button
              type="button"
              onClick={() => setStage('growing')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 12,
                border: 'none',
                backgroundColor: stage === 'growing' ? '#FFFFFF' : 'transparent',
                color: stage === 'growing' ? '#154F36' : '#6E7671',
                fontWeight: stage === 'growing' ? 850 : 650,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: stage === 'growing' ? '0 2px 8px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.2s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6
              }}
            >
              <span>🌿</span> 이미 재배 중
            </button>
          </div>
        </div>

        {/* 4. 재배 방식 (Outdoor vs Indoor Segment Control) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 방식
          </label>

          <div style={{
            display: 'flex',
            backgroundColor: '#F1F5F3',
            borderRadius: 16,
            padding: 4,
            gap: 4
          }}>
            <button
              type="button"
              onClick={() => setFarmType('outdoor')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 12,
                border: 'none',
                backgroundColor: farmType === 'outdoor' ? '#FFFFFF' : 'transparent',
                color: farmType === 'outdoor' ? '#154F36' : '#6E7671',
                fontWeight: farmType === 'outdoor' ? 850 : 650,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: farmType === 'outdoor' ? '0 2px 8px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.2s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6
              }}
            >
              <span>🏞️</span> 노지 (일반 밭)
            </button>

            <button
              type="button"
              onClick={() => setFarmType('indoor')}
              style={{
                flex: 1,
                height: 44,
                borderRadius: 12,
                border: 'none',
                backgroundColor: farmType === 'indoor' ? '#FFFFFF' : 'transparent',
                color: farmType === 'indoor' ? '#154F36' : '#6E7671',
                fontWeight: farmType === 'indoor' ? 850 : 650,
                fontSize: '0.88rem',
                cursor: 'pointer',
                boxShadow: farmType === 'indoor' ? '0 2px 8px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.2s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6
              }}
            >
              <span>🏡</span> 시설 (하우스/온실)
            </button>
          </div>
        </div>

        {/* 5. 재배 시작일 (Date Picker Selector Card) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 시작일
          </label>

          <div style={{
            backgroundColor: '#F8FAF8',
            borderRadius: 16,
            padding: '14px 16px',
            border: '1px solid #EAEFEA',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Calendar size={18} color="#2FA86A" />
              <input
                type="date"
                value={new Date().toISOString().substring(0, 10)}
                onChange={(e) => {
                  if (e.target.value) {
                    const parts = e.target.value.split('-');
                    setStartDate(`${parts[0]}년 ${parseInt(parts[1])}월 ${parseInt(parts[2])}일`);
                  }
                }}
                style={{
                  border: 'none',
                  backgroundColor: 'transparent',
                  fontSize: '0.9rem',
                  fontWeight: 800,
                  color: '#191F28',
                  outline: 'none',
                  fontFamily: 'inherit',
                  cursor: 'pointer'
                }}
              />
            </div>

            <span style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2FA86A' }}>
              {startDate}
            </span>
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
