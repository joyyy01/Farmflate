import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, MapPin } from 'lucide-react';
import { DuotoneIcon } from '../common/DuotoneIcon';
import { DatePickerSheet } from '../common/DatePickerSheet';

export interface CropRegistrationInput {
  fieldName: string;
  cropName: string;
  stage: string;
  farmType: string;
  startDate: string;
}

interface CropConditionInputViewProps {
  onBack: () => void;
  onStartAnalysis: (input: CropRegistrationInput) => void;
  onOpenExplore: () => void;
  selectedRegionName: string;
  /* Carries the in-progress form across a region-change round trip: the
     Explore screen unmounts this view, so plain useState would otherwise
     reset to blank when the user returns from picking a new region. */
  draft?: Partial<CropRegistrationInput>;
  onDraftChange?: (input: CropRegistrationInput) => void;
}

export const CropConditionInputView: React.FC<CropConditionInputViewProps> = ({
  onBack,
  onStartAnalysis,
  onOpenExplore,
  selectedRegionName,
  draft,
  onDraftChange
}) => {
  const [fieldName, setFieldName] = useState(draft?.fieldName ?? '');
  const [selectedCrop, setSelectedCrop] = useState(draft?.cropName ?? '감자');
  const [stage, setStage] = useState<'before' | 'growing'>((draft?.stage as 'before' | 'growing') ?? 'before');
  const [farmType, setFarmType] = useState<'outdoor' | 'indoor'>((draft?.farmType as 'outdoor' | 'indoor') ?? 'outdoor');
  const [rawDateIso, setRawDateIso] = useState(draft?.startDate ?? new Date().toISOString().substring(0, 10));
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);

  const emitDraft = (next: Partial<CropRegistrationInput>) => {
    onDraftChange?.({
      fieldName, cropName: selectedCrop, stage, farmType, startDate: rawDateIso,
      ...next
    });
  };

  const startDate = new Date(`${rawDateIso}T00:00:00`).toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric'
  });

  /* Available Crops */
  const crops = [
    { name: '사과', icon: 'apple' },
    { name: '배', icon: 'pear' },
    { name: '감자', icon: 'potato' },
    { name: '오이', icon: 'cucumber' },
    { name: '상추', icon: 'lettuce' }
  ];

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', justifyContent: 'space-between', padding: '24px 20px 28px 20px' }}>
      <div>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#191F28', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h2 style={{ fontSize: '1.2rem', fontWeight: 900, color: '#191F28' }}>
            농작물 등록
          </h2>
        </div>

        <div style={{ height: 1, backgroundColor: '#E5E8EB', width: '100%', marginBottom: 20 }} />

        {/* Region badge - '📍 전북 고창군' + '지역 변경' link */}
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

        {/* 밭 이름 (Field Name Input) - codex's real input wiring preserved as-is */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 8, display: 'block' }}>
            밭 이름
          </label>
          <input
            type="text"
            placeholder="예: 우리집 텃밭"
            value={fieldName}
            onChange={e => { setFieldName(e.target.value); emitDraft({ fieldName: e.target.value }); }}
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

        {/* 작물 선택 */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 12, display: 'block' }}>
            작물 선택
          </label>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
            {crops.map(crop => (
              <button
                key={crop.name}
                onClick={() => { setSelectedCrop(crop.name); emitDraft({ cropName: crop.name }); }}
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

        {/* 현재 단계 - toggle '심기 전' / '이미 재배 중' */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 10, display: 'block' }}>
            현재 단계
          </label>
          <div style={{ display: 'flex', gap: 0, border: '1px solid #E5E8EB', borderRadius: 12, overflow: 'hidden' }}>
            <button
              onClick={() => { setStage('before'); emitDraft({ stage: 'before' }); }}
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
              onClick={() => { setStage('growing'); emitDraft({ stage: 'growing' }); }}
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

        {/* 재배 방식 - toggle '노지' / '시설' */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 방식
          </label>
          <div style={{ display: 'flex', gap: 0, border: '1px solid #E5E8EB', borderRadius: 12, overflow: 'hidden' }}>
            <button
              onClick={() => { setFarmType('outdoor'); emitDraft({ farmType: 'outdoor' }); }}
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
              onClick={() => { setFarmType('indoor'); emitDraft({ farmType: 'indoor' }); }}
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

        {/* 재배 시작일 - bottom-sheet date picker (replaces codex's native <input type="date">) */}
        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 10, display: 'block' }}>
            재배 시작일
          </label>
          <button
            type="button"
            onClick={() => setIsDatePickerOpen(true)}
            style={{
              width: '100%',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '14px 16px', border: '1px solid #E5E8EB', borderRadius: 12,
              backgroundColor: '#FFFFFF', cursor: 'pointer'
            }}
          >
            <span style={{ fontSize: '0.92rem', fontWeight: 700, color: '#191F28' }}>
              {startDate}
            </span>
            <span style={{ fontSize: '0.82rem', fontWeight: 700, color: '#8B95A1' }}>
              변경
            </span>
          </button>
        </div>
      </div>

      <DatePickerSheet
        isOpen={isDatePickerOpen}
        value={rawDateIso}
        onSelect={(value) => { setRawDateIso(value); emitDraft({ startDate: value }); }}
        onClose={() => setIsDatePickerOpen(false)}
      />

      {/* Bottom CTA - submits codex's real CropRegistrationInput payload */}
      <motion.button
        whileTap={{ scale: 0.97 }}
        className="btn-farm-primary"
        onClick={() => onStartAnalysis({
          fieldName: fieldName.trim(),
          cropName: selectedCrop,
          stage,
          farmType,
          startDate: rawDateIso
        })}
      >
        적합도 분석하기
      </motion.button>
    </div>
  );
};
