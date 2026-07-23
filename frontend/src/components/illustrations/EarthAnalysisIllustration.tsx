import React from 'react';

interface EarthAnalysisIllustrationProps {
  size?: number;
  className?: string;
}

/**
 * Friendly globe mascot used on the "지역 환경 분석 중" loading screen.
 */
export const EarthAnalysisIllustration: React.FC<EarthAnalysisIllustrationProps> = ({
  size = 160,
  className = ""
}) => {
  return (
    <svg width={size} height={size} viewBox="0 0 180 180" fill="none" className={className}>
      {/* small clouds */}
      <path d="M18 60a8 8 0 0 1-1-16 10 10 0 0 1 19-3 8 8 0 0 1 6 14.5H18Z" fill="#E7EEF5" />
      <path d="M150 100a7 7 0 0 1-1-13.8 8.6 8.6 0 0 1 16.6-2.6A7 7 0 0 1 161 96h-11Z" fill="#E7EEF5" />

      {/* sparkle */}
      <path d="M140 40l2 5 5 2-5 2-2 5-2-5-5-2 5-2Z" fill="#FFD873" />

      {/* globe */}
      <circle cx="90" cy="95" r="52" fill="#6FB6E6" />
      <path
        d="M52 70c10 6 18 4 24-4 6 10 18 12 28 6 4 8 14 12 24 9a52 52 0 0 1-93 20A51.7 51.7 0 0 1 52 70Z"
        fill="#5DAA5D"
      />
      <ellipse cx="66" cy="60" rx="10" ry="6" fill="#79C279" />
      <ellipse cx="112" cy="112" rx="12" ry="7" fill="#79C279" />

      {/* face */}
      <circle cx="76" cy="96" r="3.4" fill="#183B2A" />
      <circle cx="100" cy="96" r="3.4" fill="#183B2A" />
      <path d="M78 106c4 4 16 4 20 0" stroke="#183B2A" strokeWidth="2" strokeLinecap="round" />
      <ellipse cx="68" cy="102" rx="4" ry="2.6" fill="#FF9E9E" opacity="0.7" />
      <ellipse cx="108" cy="102" rx="4" ry="2.6" fill="#FF9E9E" opacity="0.7" />

      {/* sprout on top */}
      <path d="M90 42v-8" stroke="#3F7D3E" strokeWidth="3" strokeLinecap="round" />
      <path d="M90 38c0-6-4-10-10-11 1 6 4 10 10 11Z" fill="#5DB25D" />
      <path d="M90 38c0-6 4-10 10-11-1 6-4 10-10 11Z" fill="#3F7D3E" />
    </svg>
  );
};
