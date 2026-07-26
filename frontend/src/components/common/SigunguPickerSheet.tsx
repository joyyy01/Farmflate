import React, { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { X } from 'lucide-react';
import type { RegionDto } from '../../services/api';

interface SigunguPickerSheetProps {
  isOpen: boolean;
  options: RegionDto[];
  value: string;
  onSelect: (sigunguCode: string) => void;
  onClose: () => void;
}

/* A bottom-sheet picker for 시/군/구 selection. Replaces the native <select>,
   whose dropdown direction and styling the browser controls unpredictably
   (it flips upward when the trigger sits near the bottom of the viewport) —
   this always anchors to the viewport bottom like DatePickerSheet, and adds
   arrow-key navigation on top of native scroll/click. */
export const SigunguPickerSheet: React.FC<SigunguPickerSheetProps> = ({
  isOpen, options, value, onSelect, onClose
}) => {
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    const selectedButton = listRef.current?.querySelector<HTMLButtonElement>('[data-selected="true"]');
    selectedButton?.scrollIntoView({ block: 'center' });
    selectedButton?.focus();
  }, [isOpen]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      const next = listRef.current?.querySelectorAll<HTMLButtonElement>('button')[index + 1];
      next?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const prev = listRef.current?.querySelectorAll<HTMLButtonElement>('button')[index - 1];
      prev?.focus();
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }}
      onClick={onClose}
    >
      <motion.div
        initial={{ y: '100%' }}
        animate={{ y: 0 }}
        transition={{ type: 'tween', duration: 0.25, ease: 'easeOut' }}
        onClick={e => e.stopPropagation()}
        style={{
          width: '100%', maxWidth: 480, backgroundColor: '#FFFFFF',
          borderTopLeftRadius: 28, borderTopRightRadius: 28,
          padding: '24px 0 0 0', maxHeight: '70vh', display: 'flex', flexDirection: 'column'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 24px 16px 24px' }}>
          <h3 style={{ fontSize: '1.05rem', fontWeight: 900, color: '#191F28', margin: 0 }}>
            시 / 군 / 구 선택
          </h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4, color: '#8B95A1' }} aria-label="닫기">
            <X size={22} />
          </button>
        </div>

        <div
          ref={listRef}
          className="no-scrollbar"
          style={{ overflowY: 'auto', padding: '0 16px 24px 16px', display: 'flex', flexDirection: 'column', gap: 4 }}
        >
          {options.length === 0 && (
            <div style={{ padding: '20px 12px', textAlign: 'center', color: '#8d9590', fontSize: '0.86rem' }}>
              선택한 시/도에 등록된 시/군/구 정보가 없습니다.
            </div>
          )}
          {options.map((option, index) => {
            const isSelected = option.sigunguCode === value;
            return (
              <button
                key={option.sigunguCode}
                type="button"
                data-selected={isSelected}
                onClick={() => { onSelect(option.sigunguCode || ''); onClose(); }}
                onKeyDown={e => handleKeyDown(e, index)}
                style={{
                  width: '100%', textAlign: 'left', border: 'none', cursor: 'pointer',
                  padding: '14px 12px', borderRadius: 14,
                  backgroundColor: isSelected ? '#EDF7ED' : 'transparent',
                  color: isSelected ? '#2E8B57' : '#191F28',
                  fontSize: '0.94rem', fontWeight: isSelected ? 800 : 600
                }}
              >
                {option.sigunguName || option.sigunguCode}
              </button>
            );
          })}
        </div>
      </motion.div>
    </div>
  );
};
