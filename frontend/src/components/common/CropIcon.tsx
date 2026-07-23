import React from 'react';

interface CropIconProps {
  name: string;
  size?: number;
}

export const CropIcon: React.FC<CropIconProps> = ({ name, size = 36 }) => {
  const cropKey = name.toLowerCase();

  if (cropKey.includes('사과') || cropKey.includes('apple')) {
    return (
      <svg width={size} height={size} viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="24" cy="26" r="18" fill="#EF4444" />
        <circle cx="20" cy="22" r="14" fill="#F87171" opacity="0.4" />
        <path d="M24 8C24 8 26 12 28 14" stroke="#15803D" strokeWidth="3" strokeLinecap="round" />
        <path d="M27 10C30 8 34 10 34 10C34 10 32 14 29 14C27 14 27 10 27 10Z" fill="#22C55E" />
      </svg>
    );
  }

  if (cropKey.includes('배') || cropKey.includes('pear')) {
    return (
      <svg width={size} height={size} viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M24 14C18 14 16 22 14 28C12 34 14 42 24 42C34 42 36 34 34 28C32 22 30 14 24 14Z" fill="#F59E0B" />
        <path d="M24 6C24 6 25 10 26 12" stroke="#B45309" strokeWidth="3" strokeLinecap="round" />
        <path d="M25 8C28 6 32 8 32 8C32 8 30 12 27 12Z" fill="#10B981" />
      </svg>
    );
  }

  if (cropKey.includes('오이') || cropKey.includes('cucumber')) {
    return (
      <svg width={size} height={size} viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="12" y="10" width="24" height="28" rx="12" transform="rotate(-15 24 24)" fill="#10B981" />
        <circle cx="20" cy="18" r="1.5" fill="#047857" />
        <circle cx="28" cy="24" r="1.5" fill="#047857" />
        <circle cx="22" cy="32" r="1.5" fill="#047857" />
        <path d="M16 12C14 10 12 12 12 12" stroke="#065F46" strokeWidth="2.5" strokeLinecap="round" />
      </svg>
    );
  }

  if (cropKey.includes('감자') || cropKey.includes('potato')) {
    return (
      <svg width={size} height={size} viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M14 22C12 28 14 38 24 40C34 42 40 34 38 26C36 18 30 12 22 12C14 12 16 16 14 22Z" fill="#D97706" />
        <path d="M16 24C15 28 16 36 24 38C32 40 37 34 35 28C33 22 29 14 22 14" fill="#F59E0B" opacity="0.3" />
        <circle cx="20" cy="20" r="1.5" fill="#92400E" />
        <circle cx="28" cy="26" r="1.5" fill="#92400E" />
        <circle cx="22" cy="32" r="1.5" fill="#92400E" />
      </svg>
    );
  }

  // 상추 (Lettuce) & Default Leaf
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M24 40C34 40 38 30 36 20C34 10 24 6 24 6C24 6 14 10 12 20C10 30 14 40 24 40Z" fill="#22C55E" />
      <path d="M24 40C24 40 28 28 24 12" stroke="#DCFCE7" strokeWidth="3" strokeLinecap="round" />
      <path d="M24 24C28 20 32 20 32 20" stroke="#DCFCE7" strokeWidth="2.5" strokeLinecap="round" />
      <path d="M24 30C18 26 15 26 15 26" stroke="#DCFCE7" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
};
