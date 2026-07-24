import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, MapPin } from 'lucide-react';
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
  const [startDate] = useState(
    new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
  );

  /* Design File 5 Screen 3 - '농작물 등록' */
  const crops = [
    { name: '사과', icon: 'apple' },
    { name: '배', icon: 'pear' },
    { name: '감자', icon: 'potato' },
    { name: '오이', icon: 'cucumber' },
    { name: '상추', icon: 'lettuce' }
  ];

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', justifyContent: 'space-between', paddingBottom: 28 }}>
      <div>
        {/* Header - Design File 5 Screen 3 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#191F28', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h2 style={{ fontSize: '1.2rem', fontWeight: 900, color: '#191F28' }}>
            농작물 등록
          </h2>
        </div>

        {/* Region badge - Design shows '📍 전북 고창군' + '지역 변경' link */}
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          marginBottom: 28
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.92rem', fontWeight: 800, color: '#191F28' }}>
            <MapPin size={16} color="#E53935" /> {selectedRegionName}
          </div>
          <button
            onClick={onOpenExplore}
            style={{
              background: 'none', border: 'none', fontSize: '0.82rem',
              fontWeight: 700, color: '#8B95A1', cursor: 'pointer'
            }}
          >
            지역 변경
          </button>
        </div>

        {/* 밭 이름 - Design File 5 Screen 3 */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 8, display: 'block' }}>
            밭 이름
          </label>
          <input
            type="text"
            placeholder="예: 우리집 텃밭"
            value={fieldName}
            onChange={e => setFieldName(e.target.value)}
            style={{
              width: '100%', padding: '14px 16px', border: '1px solid #E5E8EB',
              borderRadius: 12, fontSize: '0.9rem', fontWeight: 600,
              color: '#191F28', outline: 'none',
              backgroundColor: '#FFFFFF'
            }}
          />
        </div>

        {/* 작물 선택 - Design shows 5 circular crop icons in a row */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 12, display: 'block' }}>
            작물 선택
          </label>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
            {crops.map(crop => (
              <button
                key={crop.name}
                onClick={() => setSelectedCrop(crop.name)}
                style={{
                  display: 'flex', flexDirection: 'column', alignItems: 'center',
                  gap: 6, padding: '10px 6px', borderRadius: 16,
                  border: selectedCrop === crop.name ? '2px solid #2E8B57' : '1px solid transparent',
                  backgroundColor: selectedCrop === crop.name ? '#EDF7ED' : 'transparent',
                  cursor: 'pointer', flex: 1
                }}
              >
                <DuotoneIcon type={crop.icon} bgSize={44} iconSize={34} />
                <span style={{
                  fontSize: '0.78rem', fontWeight: selectedCrop === crop.name ? 800 : 600,
                  color: selectedCrop === crop.name ? '#2E8B57' : '#6B7280'
                }}>
                  {crop.name}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* 현재 단계 - Design shows toggle '심기 전' / '이미 재배 중' */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 10, display: 'block' }}>
            현재 단계
          </label>
          <div style={{ display: 'flex', gap: 0, border: '1px solid #E5E8EB', borderRadius: 12, overflow: 'hidden' }}>
            <button
              onClick={() => setStage('before')}
              style={{
                flex: 1, padding: '12px', border: 'none',
                backgroundColor: stage === 'before' ? '#FFFFFF' : '#F4F6F8',
                color: stage === 'before' ? '#191F28' : '#8B95A1',
                fontWeight: stage === 'before' ? 800 : 600,
                fontSize: '0.88rem', cursor: 'pointer'
              }}
            >
              심기 전
            </button>
            <button
              onClick={() => setStage('growing')}
              style={{
                flex: 1, padding: '12px', border: 'none',
                borderLeft: '1px solid #E5E8EB',
                backgroundColor: stage === 'growing' ? '#FFFFFF' : '#F4F6F8',
                color: stage === 'growing' ? '#191F28' : '#8B95A1',
                fontWeight: stage === 'growing' ? 800 : 600,
                fontSize: '0.88rem', cursor: 'pointer'
              }}
            >
              이미 재배 중
            </button>
          </div>
        </div>

        {/* 재배 방식 - Design shows toggle '노지' / '시설' */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 방식
          </label>
          <div style={{ display: 'flex', gap: 0, border: '1px solid #E5E8EB', borderRadius: 12, overflow: 'hidden' }}>
            <button
              onClick={() => setFarmType('outdoor')}
              style={{
                flex: 1, padding: '12px', border: 'none',
                backgroundColor: farmType === 'outdoor' ? '#FFFFFF' : '#F4F6F8',
                color: farmType === 'outdoor' ? '#191F28' : '#8B95A1',
                fontWeight: farmType === 'outdoor' ? 800 : 600,
                fontSize: '0.88rem', cursor: 'pointer'
              }}
            >
              노지
            </button>
            <button
              onClick={() => setFarmType('indoor')}
              style={{
                flex: 1, padding: '12px', border: 'none',
                borderLeft: '1px solid #E5E8EB',
                backgroundColor: farmType === 'indoor' ? '#FFFFFF' : '#F4F6F8',
                color: farmType === 'indoor' ? '#191F28' : '#8B95A1',
                fontWeight: farmType === 'indoor' ? 800 : 600,
                fontSize: '0.88rem', cursor: 'pointer'
              }}
            >
              시설
            </button>
          </div>
        </div>

        {/* 재배 시작일 */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 시작일
          </label>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '14px 16px', border: '1px solid #E5E8EB', borderRadius: 12
          }}>
            <span style={{ fontSize: '0.92rem', fontWeight: 700, color: '#191F28' }}>
              {startDate}
            </span>
            <button style={{
              background: 'none', border: 'none', fontSize: '0.82rem',
              fontWeight: 700, color: '#8B95A1', cursor: 'pointer'
            }}>
              변경
            </button>
          </div>
        </div>
      </div>

      {/* Bottom button - '적합도 분석하기' */}
      <motion.button
        whileTap={{ scale: 0.97 }}
        className="btn-farm-primary"
        onClick={() => onStartAnalysis(selectedCrop, stage, farmType)}
      >
        적합도 분석하기
      </motion.button>
    </div>
  );
};
