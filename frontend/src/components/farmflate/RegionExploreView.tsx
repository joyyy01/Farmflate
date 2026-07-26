import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronDown, MapPin } from 'lucide-react';
import { ApiError, ApiService } from '../../services/api';
import type { RegionAnalysisRequest, RegionDto } from '../../services/api';
import { SigunguPickerSheet } from '../common/SigunguPickerSheet';
import { BackButton } from '../common/BackButton';

interface RegionExploreViewProps {
  onBack: () => void;
  onStartAnalysis: (request: Omit<RegionAnalysisRequest, 'idempotencyKey'>) => void;
  mode?: 'analyze' | 'change';
  /** 'change' mode resolves the region silently in the background instead of
      navigating to the full analyzing screen; these reflect that in-flight state. */
  isSubmitting?: boolean;
  submitError?: string | null;
}

export const RegionExploreView: React.FC<RegionExploreViewProps> = ({
  onBack,
  onStartAnalysis,
  mode = 'analyze',
  isSubmitting = false,
  submitError = null
}) => {
  const [provinces, setProvinces] = useState<RegionDto[]>([]);
  const [districts, setDistricts] = useState<RegionDto[]>([]);
  const [selectedProvinceCode, setSelectedProvinceCode] = useState('');
  const [selectedDistrictCode, setSelectedDistrictCode] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isDistrictPickerOpen, setIsDistrictPickerOpen] = useState(false);

  const selectedProvince = provinces.find(region => region.sidoCode === selectedProvinceCode);
  const selectedDistrict = districts.find(region => region.sigunguCode === selectedDistrictCode);

  const loadProvinces = async (force = false) => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const next = force ? await ApiService.getSidos({ force: true }) : await ApiService.getSidos();
      setProvinces(next);
      if (next.length === 0) {
        setLoadError('등록된 시/도 정보가 없습니다. 잠시 후 다시 시도해 주세요.');
      }
      if (mode === 'change') {
        setSelectedProvinceCode(previous => previous || next[0]?.sidoCode || '');
      }
    } catch (error) {
      setLoadError(error instanceof ApiError ? error.message : '지역 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { void loadProvinces(); }, []);

  useEffect(() => {
    if (!selectedProvinceCode) {
      setDistricts([]);
      setSelectedDistrictCode('');
      return;
    }
    let isCurrent = true;
    setLoadError(null);
    void ApiService.getSigungus(selectedProvinceCode)
      .then(next => {
        if (!isCurrent) return;
        setDistricts(next);
        if (next.length === 0) {
          setLoadError('선택한 시/도에 등록된 시/군/구 정보가 없습니다.');
        }
      })
      .catch(error => { if (isCurrent) setLoadError(error instanceof ApiError ? error.message : '시/군/구 목록을 불러오지 못했습니다.'); });
    return () => { isCurrent = false; };
  }, [selectedProvinceCode]);

  const handleProvinceSelect = (code: string) => {
    setSelectedProvinceCode(code);
    setSelectedDistrictCode('');
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
          <BackButton onClick={onBack} disabled={isSubmitting} style={{ color: '#202A24' }} />
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            {mode === 'change' ? '지역 변경' : '지역 탐색'}
          </h1>
          <div />
        </div>

        {/* Title Section */}
        <div style={{ marginBottom: 24 }}>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#154F36', marginBottom: 6, lineHeight: 1.35 }}>
            {mode === 'change' ? '변경할 지역을 선택해주세요!' : (
              <>살고 계신 (또는 귀농 예정인)<br />지역의 농사 환경을 먼저 살펴보세요</>
            )}
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#6F7772', fontWeight: 500, margin: 0 }}>
            시/도를 선택하면 해당 지역의 시/군/구 목록이 자동으로 나타나요.
          </p>
        </div>

        {loadError && (
          <div role="alert" style={{ backgroundColor: '#FFF4F2', border: '1px solid #F3CCC5', borderRadius: 14, padding: '12px 14px', marginBottom: 20, color: '#A43A2F', fontSize: '0.82rem', fontWeight: 650 }}>
            {loadError}
            <button type="button" onClick={() => void loadProvinces(true)} style={{ marginLeft: 8, border: 0, background: 'none', color: '#A43A2F', textDecoration: 'underline', fontWeight: 800 }}>다시 시도</button>
          </div>
        )}

        {/* Province Chips */}
        <div style={{ marginBottom: 28 }}>
          <h3 style={{ fontSize: '0.96rem', fontWeight: 850, color: '#154F36', marginBottom: 12 }}>
            시 / 도 선택
          </h3>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {provinces.map(p => {
              const isSelected = p.sidoCode === selectedProvinceCode;
              return (
                <button
                  key={p.sidoCode}
                  type="button"
                  onClick={() => handleProvinceSelect(p.sidoCode)}
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
                  {p.sidoName || p.sidoCode}
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
            <button
              type="button"
              disabled={!selectedProvinceCode}
              onClick={() => setIsDistrictPickerOpen(true)}
              style={{
                width: '100%',
                height: 52,
                borderRadius: 16,
                border: '1.5px solid #2FA86A',
                backgroundColor: '#FFFFFF',
                padding: '0 44px 0 18px',
                fontSize: '0.96rem',
                fontWeight: 750,
                color: selectedDistrict ? '#191F28' : '#8B95A1',
                outline: 'none',
                textAlign: 'left',
                cursor: selectedProvinceCode ? 'pointer' : 'not-allowed',
                opacity: selectedProvinceCode ? 1 : 0.6
              }}
            >
              {selectedDistrict?.sigunguName || '시/군/구 선택'}
            </button>
            <ChevronDown size={22} color="#2FA86A" style={{ position: 'absolute', right: 16, top: 15, pointerEvents: 'none' }} />
          </div>
        </div>

        <SigunguPickerSheet
          isOpen={isDistrictPickerOpen}
          options={districts}
          value={selectedDistrictCode}
          onSelect={setSelectedDistrictCode}
          onClose={() => setIsDistrictPickerOpen(false)}
        />

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
            <span style={{ fontSize: '0.76rem', color: '#2FA86A', fontWeight: 750 }}>{mode === 'change' ? '선택된 지역' : '선택된 분석 지역'}</span>
            <div style={{ fontSize: '1.15rem', fontWeight: 900, color: '#154F36', marginTop: 2 }}>
              {[selectedProvince?.sidoName, selectedDistrict?.sigunguName].filter(Boolean).join(' ') || '지역을 선택해 주세요'}
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
        {submitError && (
          <div role="alert" style={{ backgroundColor: '#FFF4F2', border: '1px solid #F3CCC5', borderRadius: 14, padding: '10px 14px', marginBottom: 12, color: '#A43A2F', fontSize: '0.82rem', fontWeight: 650 }}>
            {submitError}
          </div>
        )}
        <motion.button
          whileTap={{ scale: 0.98 }}
          disabled={isLoading || isSubmitting || !selectedProvince || !selectedDistrict}
          onClick={() => {
            if (!selectedProvince || !selectedDistrict) return;
            onStartAnalysis({
              sidoCode: selectedProvince.sidoCode,
              sidoName: selectedProvince.sidoName || selectedProvince.sidoCode,
              sigunguCode: selectedDistrict.sigunguCode || '',
              sigunguName: selectedDistrict.sigunguName || selectedDistrict.sigunguCode || ''
            });
          }}
          className="btn-farm-primary"
          style={{ width: '100%', height: 56, fontSize: '1.05rem', borderRadius: 16, opacity: isSubmitting ? 0.7 : 1 }}
        >
          {isSubmitting ? '지역 확인 중...' : (mode === 'change' ? '지역 변경하기' : '지역 환경 분석하기')}
        </motion.button>
      </div>
    </div>
  );
};
