import React, { useEffect, useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const ROTATION_MS = 10_000;

export interface RecommendedCrop {
  rank?: number | null;
  cropCode?: string | null;
  cropName?: string | null;
  score?: number | null;
  reason?: string | null;
}

interface RecommendedCropCarouselProps {
  crops: RecommendedCrop[];
}

export const RecommendedCropCarousel: React.FC<RecommendedCropCarouselProps> = ({ crops }) => {
  const ordered = useMemo(
    () => [...crops].sort((a, b) => (a.rank ?? 999) - (b.rank ?? 999)).slice(0, 3),
    [crops],
  );
  const [index, setIndex] = useState(0);
  const key = ordered.map(c => c.cropCode).join('|');

  useEffect(() => {
    setIndex(0);
  }, [key]);

  useEffect(() => {
    if (ordered.length <= 1) return;
    if (typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        setIndex(current => (current + 1) % ordered.length);
      }
    }, ROTATION_MS);

    return () => window.clearInterval(timer);
  }, [ordered.length]);

  if (ordered.length === 0) {
    return (
      <div
        style={{
          backgroundColor: '#E4F3E7', borderRadius: 20,
          height: 60, width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        <span style={{ fontSize: '0.9rem', fontWeight: 400, color: '#154F36', letterSpacing: '-0.02em' }}>
          아직 추천 정보가 없어요
        </span>
      </div>
    );
  }

  const crop = ordered[index];

  return (
    <div>
      <div style={{ position: 'relative', overflow: 'hidden' }}>
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={crop.cropCode ?? index}
            initial={{ x: 24, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: -24, opacity: 0 }}
            transition={{ duration: 0.3 }}
            aria-live="off"
            style={{
              width: '100%', border: '1px solid #E5E8EB', borderRadius: 20, backgroundColor: '#F8FAF8',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 18px',
              boxSizing: 'border-box',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <img src="/svg-assets/crops/sprout.svg" alt="" aria-hidden="true" style={{ width: 44, height: 44, objectFit: 'contain' }} />
              <div>
                <strong style={{ fontSize: '0.9rem', color: '#154F36', fontWeight: 850 }}>
                  TOP {crop.rank ?? index + 1} 추천: {crop.cropName} ({typeof crop.score === 'number' ? `${crop.score}점` : '점수 정보 없음'})
                </strong>
                <p style={{ margin: '3px 0 0 0', fontSize: '0.78rem', color: '#6E7671', fontWeight: 500, lineHeight: 1.4 }}>
                  {crop.reason || '추천 근거가 제공되지 않았습니다.'}
                </p>
              </div>
            </div>
          </motion.div>
        </AnimatePresence>
      </div>
      {ordered.length > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: 6, marginTop: 8 }}>
          {ordered.map((c, i) => (
            <button
              key={c.cropCode ?? i}
              type="button"
              aria-label={`TOP ${c.rank ?? i + 1} 추천 작물 보기`}
              aria-current={i === index}
              onClick={() => setIndex(i)}
              style={{
                width: 7, height: 7, borderRadius: '50%', border: 'none', padding: 0,
                backgroundColor: i === index ? '#2E9F5B' : '#D8E5DB', cursor: 'pointer',
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
};
