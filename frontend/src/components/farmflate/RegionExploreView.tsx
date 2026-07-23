import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, ChevronDown } from 'lucide-react';
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
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', padding: '0', display: 'flex', flexDirection: 'column' }}>
      <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '24px 20px 24px 20px', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '30px 1fr 30px',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #ECEFED',
          marginBottom: 16
        }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202A24', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            지역 탐색
          </h1>
          <div />
        </div>

        {/* Title */}
        <h2 style={{ fontSize: '1.4rem', fontWeight: 900, color: '#154F36', marginBottom: 6, lineHeight: 1.3 }}>
          살고 계신 (또는 귀농 예정인)<br />지역의 농사 환경을 먼저 살펴보세요
        </h2>
        <p style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 500, marginBottom: 20 }}>
          시/도를 선택하면 해당 지역의 시/군/구 목록이 자동으로 나타나요.
        </p>

        {/* Province RegionChips with NO-SCROLLBAR class */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.94rem', fontWeight: 850, color: '#154F36', marginBottom: 12 }}>
            시 / 도 선택
          </h3>

          <div className="no-scrollbar" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {provinces.map(p => {
              const isSelected = p === selectedProvince;
              return (
                <button
                  key={p}
                  type="button"
                  onClick={() => handleProvinceSelect(p)}
                  style={{
                    padding: '8px 14px',
                    borderRadius: 20,
                    fontSize: '0.82rem',
                    fontWeight: isSelected ? 850 : 600,
                    backgroundColor: isSelected ? '#2FA86A' : '#F8FAF8',
                    color: isSelected ? '#FFFFFF' : '#4E5968',
                    border: isSelected ? 'none' : '1px solid #E1E8E4',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease'
                  }}
                >
                  {p}
                </button>
              );
            })}
          </div>
        </div>

        {/* District Select */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '0.94rem', fontWeight: 850, color: '#154F36', marginBottom: 6 }}>
            시 / 군 / 구 선택
          </h3>

          <div style={{ position: 'relative' }}>
            <select
              value={selectedDistrict}
              onChange={e => setSelectedDistrict(e.target.value)}
              style={{
                width: '100%',
                height: 52,
                borderRadius: 14,
                border: '1.5px solid #2FA86A',
                backgroundColor: '#FFFFFF',
                padding: '0 16px',
                fontSize: '0.94rem',
                fontWeight: 700,
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
            <ChevronDown size={20} color="#2FA86A" style={{ position: 'absolute', right: 16, top: 16, pointerEvents: 'none' }} />
          </div>
        </div>

        {/* Selected Region Card */}
        <div style={{
          backgroundColor: '#E9F7EC',
          borderRadius: 16,
          padding: '16px 20px',
          border: '1px solid #A8DCC8',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <div>
            <span style={{ fontSize: '0.74rem', color: '#1E5E3E', fontWeight: 700 }}>선택된 분석 지역</span>
            <div style={{ fontSize: '1.1rem', fontWeight: 900, color: '#154F36', marginTop: 2 }}>
              {selectedProvince} {selectedDistrict}
            </div>
          </div>
          <span style={{ fontSize: '1.8rem' }}>📍</span>
        </div>
      </div>

      {/* Primary CTA Button (Fixed at Bottom) */}
      <div style={{ padding: '16px 20px 32px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F0F2F1' }}>
        <motion.button
          whileTap={{ scale: 0.97 }}
          onClick={() => onStartAnalysis(selectedProvince, selectedDistrict)}
          style={{
            width: '100%',
            height: 52,
            borderRadius: 26,
            background: 'linear-gradient(135deg, #2fa86a, #38a766)',
            color: '#FFFFFF',
            fontSize: '1rem',
            fontWeight: 850,
            border: 'none',
            cursor: 'pointer',
            boxShadow: '0 8px 18px rgba(47, 168, 106, 0.18)'
          }}
        >
          지역 환경 분석하기
        </motion.button>
      </div>
    </div>
  );
};
