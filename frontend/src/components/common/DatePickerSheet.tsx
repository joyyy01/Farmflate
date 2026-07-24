import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface DatePickerSheetProps {
  isOpen: boolean;
  value: string; // YYYY-MM-DD
  onSelect: (value: string) => void;
  onClose: () => void;
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

export const DatePickerSheet: React.FC<DatePickerSheetProps> = ({ isOpen, value, onSelect, onClose }) => {
  const parsed = value ? new Date(`${value}T00:00:00`) : new Date();
  const [viewYear, setViewYear] = useState(parsed.getFullYear());
  const [viewMonth, setViewMonth] = useState(parsed.getMonth());

  useEffect(() => {
    if (isOpen) {
      const d = value ? new Date(`${value}T00:00:00`) : new Date();
      setViewYear(d.getFullYear());
      setViewMonth(d.getMonth());
    }
  }, [isOpen, value]);

  const firstDayOfMonth = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();

  const cells: Array<number | null> = [];
  for (let i = 0; i < firstDayOfMonth; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const goPrevMonth = () => {
    if (viewMonth === 0) { setViewMonth(11); setViewYear(y => y - 1); } else setViewMonth(m => m - 1);
  };
  const goNextMonth = () => {
    if (viewMonth === 11) { setViewMonth(0); setViewYear(y => y + 1); } else setViewMonth(m => m + 1);
  };

  const handleSelectDay = (day: number) => {
    const mm = String(viewMonth + 1).padStart(2, '0');
    const dd = String(day).padStart(2, '0');
    onSelect(`${viewYear}-${mm}-${dd}`);
    onClose();
  };

  const selectedDate = value ? new Date(`${value}T00:00:00`) : null;
  const today = new Date();

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
        style={{ width: '100%', maxWidth: 480, backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: 24 }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <button onClick={goPrevMonth} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4, color: '#191F28' }}>
            <ChevronLeft size={22} />
          </button>
          <h3 style={{ fontSize: '1.05rem', fontWeight: 900, color: '#191F28', margin: 0 }}>
            {viewYear}년 {viewMonth + 1}월
          </h3>
          <button onClick={goNextMonth} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4, color: '#191F28' }}>
            <ChevronRight size={22} />
          </button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', marginBottom: 4 }}>
          {WEEKDAYS.map(w => (
            <div key={w} style={{ textAlign: 'center', fontSize: '0.78rem', fontWeight: 700, color: '#8B95A1', padding: '6px 0' }}>
              {w}
            </div>
          ))}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 2, marginBottom: 8 }}>
          {cells.map((day, idx) => {
            if (day === null) return <div key={idx} />;
            const isSelected = !!selectedDate
              && selectedDate.getFullYear() === viewYear
              && selectedDate.getMonth() === viewMonth
              && selectedDate.getDate() === day;
            const isToday = today.getFullYear() === viewYear && today.getMonth() === viewMonth && today.getDate() === day;
            return (
              <button
                key={idx}
                onClick={() => handleSelectDay(day)}
                style={{
                  aspectRatio: '1',
                  border: isToday && !isSelected ? '1px solid #2FA86A' : 'none',
                  borderRadius: '50%',
                  backgroundColor: isSelected ? '#2FA86A' : 'transparent',
                  color: isSelected ? '#FFFFFF' : '#191F28',
                  fontSize: '0.88rem',
                  fontWeight: isSelected ? 800 : 600,
                  cursor: 'pointer'
                }}
              >
                {day}
              </button>
            );
          })}
        </div>
      </motion.div>
    </div>
  );
};
