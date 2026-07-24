import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, ChevronDown, MapPin } from 'lucide-react';
import { KOREA_REGIONS } from '../../services/farmEngine';

interface RegionExploreViewProps {
  onBack: () => void;
  onStartAnalysis: (province: string, district: string) => void;
}

export const RegionExploreView: React.FC<RegionExploreViewProps> = ({
  onBack,
  onStartAnalysis
}) => {
  const provinces = Object.keys(KOREA_REGIONS);
  const [selectedProvince, setSelectedProvince] = useState('전북특별자치도');
  const districts = KOREA_REGIONS[selectedProvince] || [];
  const [selectedDistrict, setSelectedDistrict] = useState(districts[0] || '고창군');

  const handleProvinceSelect = (p: string) => {
    setSelectedProvince(p);
    const newDistricts = KOREA_REGIONS[p] || [];
    setSelectedDistrict(newDistricts[0] || '');
  };

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', display: 'flex', flexDirection: 'column', height: '100%', padding: 0 }}>
      {/* Scrollable Content Area */}
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '32px 20px 24px 20px', display: 'flex', flexDirection: 'column' }}>
        
        {/* Header */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '30px 1fr 30px',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #F0F2F1',
          marginBottom: 20
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202A24', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            지역 탐색
          </h1>
          <div />
        </div>

        {/* Title Section */}
        <div style={{ marginBottom: 24 }}>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#154F36', marginBottom: 6, lineHeight: 1.35 }}>
            살고 계신 (또는 귀농 예정인)<br />지역의 농사 환경을 먼저 살펴보세요
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#6F7772', fontWeight: 500, margin: 0 }}>
            시/도를 선택하면 해당 지역의 시/군/구 목록이 자동으로 나타나요.
          </p>
        </div>

        {/* Province Chips */}
        <div style={{ marginBottom: 28 }}>
          <h3 style={{ fontSize: '0.96rem', fontWeight: 850, color: '#154F36', marginBottom: 12 }}>
            시 / 도 선택
          </h3>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {provinces.map(p => {
              const isSelected = p === selectedProvince;
              return (
                <button
                  key={p}
                  type="button"
                  onClick={() => handleProvinceSelect(p)}
                  style={{
                    padding: '8px 15px',
                    borderRadius: 20,
                    fontSize: '0.84rem',
                    fontWeight: isSelected ? 800 : 500,
                    backgroundColor: isSelected ? '#2FA86A' : '#F8FAF8',
                    color: isSelected ? '#FFFFFF' : '#4E5968',
                    border: isSelected ? '1.5px solid #2FA86A' : '1.5px solid #EAEFEA',
                    boxSizing: 'border-box',
                    cursor: 'pointer',
                    transition: 'background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease'
                  }}
                >
                  {p}
                </button>
              );
            })}
          </div>
        </div>

        {/* District Select */}
        <div style={{ marginBottom: 28 }}>
          <h3 style={{ fontSize: '0.96rem', fontWeight: 850, color: '#154F36', marginBottom: 8 }}>
            시 / 군 / 구 선택
          </h3>

          <div style={{ position: 'relative' }}>
            <select
              value={selectedDistrict}
              onChange={e => setSelectedDistrict(e.target.value)}
              style={{
                width: '100%',
                height: 52,
                borderRadius: 16,
                border: '1.5px solid #2FA86A',
                backgroundColor: '#FFFFFF',
                padding: '0 44px 0 18px',
                fontSize: '0.96rem',
                fontWeight: 750,
                color: '#191F28',
                outline: 'none',
                appearance: 'none',
                cursor: 'pointer'
              }}
            >
              {districts.map(d => (
                <option key={d} value={d}>{d}</option>
              ))}
            </select>
            <ChevronDown size={22} color="#2FA86A" style={{ position: 'absolute', right: 16, top: 15, pointerEvents: 'none' }} />
          </div>
        </div>

        {/* Selected Region Card */}
        <div style={{
          background: 'linear-gradient(135deg, #F0FAF3, #F7FCF8)',
          borderRadius: 20,
          padding: '18px 20px',
          border: '1px solid #D1EBE0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <div>
            <span style={{ fontSize: '0.76rem', color: '#2FA86A', fontWeight: 750 }}>선택된 분석 지역</span>
            <div style={{ fontSize: '1.15rem', fontWeight: 900, color: '#154F36', marginTop: 2 }}>
              {selectedProvince} {selectedDistrict}
            </div>
          </div>
          <div style={{
            width: 42, height: 42, borderRadius: '50%', backgroundColor: '#FFFFFF',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            border: '1px solid #D1EBE0'
          }}>
            <MapPin size={22} color="#2FA86A" />
          </div>
        </div>
      </div>

      {/* Fixed Bottom CTA Button */}
      <div style={{ padding: '16px 20px 32px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F0F2F1' }}>
        <motion.button
          whileTap={{ scale: 0.98 }}
          onClick={() => onStartAnalysis(selectedProvince, selectedDistrict)}
          className="btn-farm-primary"
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16 }}
        >
          지역 환경 분석하기
        </motion.button>
      </div>
    </div>
  );
};
